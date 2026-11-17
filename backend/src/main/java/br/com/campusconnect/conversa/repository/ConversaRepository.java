package br.com.campusconnect.conversa.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As conversas diretas e a leitura de cada participante.
 *
 * <p>Cada escrita é uma instrução só, com a condição no {@code WHERE}, como no módulo de conexões: duas pessoas
 * abrindo a mesma conversa ao mesmo tempo ficam com uma conversa só. A lista é paginada por <em>keyset</em> na data
 * da última mensagem, como o feed.
 *
 * <p>O SQL é montado só com trechos fixos daqui; o que vem de fora entra sempre como parâmetro.
 */
@Repository
@RequiredArgsConstructor
public class ConversaRepository {

    /** O outro lado de uma conversa direta, visto por {@code :eu}. */
    private static final String OUTRO = """
            case when c.usuario_menor_id = :eu then c.usuario_maior_id else c.usuario_menor_id end
            """;

    /** Há mensagem da outra pessoa, não apagada, depois da leitura de {@code :eu} (participante {@code p}). */
    private static final String NAO_LIDA = """
            exists (select 1 from mensagem n
                    where n.conversa_id = c.id and n.remetente_id <> :eu and n.removido_em is null
                      and n.enviado_em > coalesce(p.ultima_leitura_em, '-infinity'::timestamptz))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * A conversa vista por um participante.
     *
     * @param outroLeuEm até quando a outra pessoa leu; {@code null} se ela nunca leu
     * @param ultimaMensagemEm {@code null} enquanto a conversa está vazia
     */
    public record Participacao(UUID conversaId, UUID outroId, @Nullable Instant outroLeuEm,
            @Nullable Instant ultimaMensagemEm) {
    }

    /** Uma conversa na lista, com a última mensagem. */
    public record Linha(UUID conversaId, UUID outroId, Instant ultimaMensagemEm, UUID mensagemId, UUID remetenteId,
            @Nullable String conteudo, Instant enviadoEm, boolean apagada, boolean naoLida) {
    }

    public Optional<UUID> idDoPar(UUID menor, UUID maior) {
        return jdbc.query("""
                select id from conversa where usuario_menor_id = :menor and usuario_maior_id = :maior
                """, new MapSqlParameterSource("menor", menor).addValue("maior", maior),
                (linha, numero) -> linha.getObject("id", UUID.class)).stream().findFirst();
    }

    /** Cria a conversa direta do par com os dois participantes, ou devolve a que já existe. */
    public UUID criarDireta(UUID menor, UUID maior) {
        MapSqlParameterSource par = new MapSqlParameterSource("menor", menor).addValue("maior", maior);
        jdbc.update("""
                insert into conversa (tipo, usuario_menor_id, usuario_maior_id) values ('DIRETA', :menor, :maior)
                on conflict (usuario_menor_id, usuario_maior_id) do nothing
                """, par);
        UUID id = idDoPar(menor, maior).orElseThrow();
        jdbc.update("""
                insert into participante_conversa (conversa_id, usuario_id) values (:id, :menor), (:id, :maior)
                on conflict (conversa_id, usuario_id) do nothing
                """, par.addValue("id", id));
        return id;
    }

    /** A conversa direta, se {@code eu} participa dela. */
    public Optional<Participacao> participacao(UUID conversaId, UUID eu) {
        return jdbc.query("""
                select c.id, %s as outro_id, o.ultima_leitura_em as outro_leu_em, c.ultima_mensagem_em
                from conversa c
                join participante_conversa p on p.conversa_id = c.id and p.usuario_id = :eu
                left join participante_conversa o on o.conversa_id = c.id and o.usuario_id <> :eu
                where c.id = :id and c.tipo = 'DIRETA'
                """.formatted(OUTRO), new MapSqlParameterSource("id", conversaId).addValue("eu", eu),
                (linha, numero) -> new Participacao(linha.getObject("id", UUID.class),
                        linha.getObject("outro_id", UUID.class), instante(linha, "outro_leu_em"),
                        instante(linha, "ultima_mensagem_em"))).stream().findFirst();
    }

    /** O outro lado de cada conversa com mensagem de que {@code eu} participa. */
    public List<UUID> outrosDe(UUID eu) {
        return jdbc.query("""
                select %s as outro_id from conversa c
                where (c.usuario_menor_id = :eu or c.usuario_maior_id = :eu) and c.ultima_mensagem_em is not null
                """.formatted(OUTRO), new MapSqlParameterSource("eu", eu),
                (linha, numero) -> linha.getObject("outro_id", UUID.class));
    }

    /**
     * As conversas com mensagem de {@code eu} com as pessoas dadas, da última mensagem mais recente para a mais antiga.
     *
     * @param outros só as conversas com estas pessoas; o serviço já tirou as contas que não aparecem
     */
    public List<Linha> listar(UUID eu, Collection<UUID> outros, boolean soNaoLidas, @Nullable Instant antesDe,
            @Nullable UUID antesDoId, int limite) {
        if (outros.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("eu", eu)
                .addValue("outros", outros)
                .addValue("limite", limite);
        StringBuilder sql = new StringBuilder("""
                select c.id, o.outro_id, c.ultima_mensagem_em, u.id as mensagem_id, u.remetente_id, u.conteudo,
                       u.enviado_em, u.removido_em is not null as apagada, %s as nao_lida
                from conversa c
                join participante_conversa p on p.conversa_id = c.id and p.usuario_id = :eu
                cross join lateral (select %s as outro_id) o
                join lateral (select m.id, m.remetente_id, m.conteudo, m.enviado_em, m.removido_em from mensagem m
                              where m.conversa_id = c.id order by m.enviado_em desc, m.id desc limit 1) u on true
                where c.ultima_mensagem_em is not null and o.outro_id in (:outros)
                """.formatted(NAO_LIDA, OUTRO));
        if (soNaoLidas) {
            sql.append(" and ").append(NAO_LIDA);
        }
        if (antesDe != null && antesDoId != null) {
            sql.append(" and (c.ultima_mensagem_em, c.id) < (:antesDe, :antesDoId)");
            parametros.addValue("antesDe", data(antesDe)).addValue("antesDoId", antesDoId);
        }
        sql.append(" order by c.ultima_mensagem_em desc, c.id desc limit :limite");
        return jdbc.query(sql.toString(), parametros, ConversaRepository::linha);
    }

    /** Quantas conversas de {@code eu} com as pessoas dadas têm mensagem não lida. */
    public long contarNaoLidas(UUID eu, Collection<UUID> outros) {
        if (outros.isEmpty()) {
            return 0;
        }
        Long total = jdbc.queryForObject("""
                select count(*) from conversa c
                join participante_conversa p on p.conversa_id = c.id and p.usuario_id = :eu
                where c.ultima_mensagem_em is not null and %s in (:outros) and %s
                """.formatted(OUTRO, NAO_LIDA), new MapSqlParameterSource("eu", eu).addValue("outros", outros),
                Long.class);
        return total == null ? 0 : total;
    }

    /** A data da última mensagem só avança: um envio que confirma depois de outro mais novo não a faz voltar. */
    public void registrarEnvio(UUID conversaId, Instant enviadoEm) {
        jdbc.update("""
                update conversa set ultima_mensagem_em = greatest(coalesce(ultima_mensagem_em, '-infinity'), :em)
                where id = :id
                """, new MapSqlParameterSource("id", conversaId).addValue("em", data(enviadoEm)));
    }

    /** Quem manda uma mensagem leu a conversa até ela. */
    public void avancarLeitura(UUID conversaId, UUID eu, Instant ate) {
        jdbc.update("""
                update participante_conversa
                set ultima_leitura_em = greatest(coalesce(ultima_leitura_em, '-infinity'), :ate)
                where conversa_id = :id and usuario_id = :eu
                """, new MapSqlParameterSource("id", conversaId).addValue("eu", eu).addValue("ate", data(ate)));
    }

    /**
     * Marca a conversa como lida por {@code eu} até a mensagem dada, pela data dela no banco. Nunca volta: ler até uma
     * mensagem mais antiga que a última leitura não muda nada.
     *
     * @return a nova leitura, ou vazio se nada mudou (a mensagem não é desta conversa ou já estava lida)
     */
    public Optional<Instant> lerAte(UUID conversaId, UUID eu, UUID mensagemId) {
        return jdbc.query("""
                update participante_conversa p set ultima_leitura_em = m.enviado_em
                from mensagem m
                where m.id = :mensagem and m.conversa_id = :id and p.conversa_id = :id and p.usuario_id = :eu
                  and (p.ultima_leitura_em is null or p.ultima_leitura_em < m.enviado_em)
                returning p.ultima_leitura_em
                """, new MapSqlParameterSource("id", conversaId).addValue("eu", eu).addValue("mensagem", mensagemId),
                (linha, numero) -> instante(linha, "ultima_leitura_em")).stream().findFirst();
    }

    // ------------------------------------------------------------ apoio

    /** O driver do PostgreSQL não aceita {@link Instant} como parâmetro; {@link OffsetDateTime} sim. */
    static OffsetDateTime data(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    static @Nullable Instant instante(ResultSet linha, String coluna) throws SQLException {
        OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static Linha linha(ResultSet linha, int numero) throws SQLException {
        return new Linha(linha.getObject("id", UUID.class), linha.getObject("outro_id", UUID.class),
                instante(linha, "ultima_mensagem_em"), linha.getObject("mensagem_id", UUID.class),
                linha.getObject("remetente_id", UUID.class), linha.getString("conteudo"),
                instante(linha, "enviado_em"), linha.getBoolean("apagada"), linha.getBoolean("nao_lida"));
    }
}
