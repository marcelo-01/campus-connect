package br.com.campusconnect.notificacao.repository;

import br.com.campusconnect.notificacao.model.TipoNotificacao;
import br.com.campusconnect.notificacao.model.TipoNotificacao.Repeticao;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As notificações de cada pessoa. A tela mostra grupos: as curtidas e as republicações do mesmo alvo formam um grupo
 * só, e cada outra notificação é um grupo sozinha. O grupo tem uma chave, que é o tipo e o alvo nos que agrupam e o
 * id da linha nos outros, e a data da linha mais nova dele.
 *
 * <p>Os comandos que apagam devolvem de quem eram as linhas apagadas, para o contador dessas pessoas se acertar ao
 * vivo.
 */
@Repository
@RequiredArgsConstructor
public class NotificacaoRepository {

    /** O {@code WHERE} do índice {@code ux_notificacao_uma_por_pessoa}: o {@code ON CONFLICT} precisa dele igual. */
    private static final String UMA_POR_PESSOA =
            "tipo in (" + tipos(tipo -> tipo.repeticao() != Repeticao.SEMPRE) + ")";

    private static final String CHAVE = """
            (case when n.tipo in (%s)
                  then n.tipo || ':' || coalesce(n.referencia_id::text, '') || ':' || coalesce(n.detalhe_id::text, '')
                  else n.id::text end)""".formatted(tipos(TipoNotificacao::agrupa));

    private static final String INSERIR = """
            insert into notificacao (destinatario_id, origem_id, tipo, referencia_id, detalhe_id, trecho)
            values (:destinatario, :origem, :tipo, :referencia, :detalhe, :trecho)
            """;

    private static final String CONFLITO = " on conflict (destinatario_id, tipo, referencia_id, detalhe_id, origem_id) "
            + "where " + UMA_POR_PESSOA;

    private final NamedParameterJdbcTemplate jdbc;

    /** @param trecho o começo do comentário, já cortado */
    public record Nova(UUID destinatario, UUID origem, TipoNotificacao tipo, @Nullable UUID referencia,
            @Nullable UUID detalhe, @Nullable String trecho) {
    }

    /**
     * Uma notificação na tela.
     *
     * @param em a data da linha mais nova do grupo
     * @param total quantas linhas, ou seja, quantas pessoas, já que cada uma avisa uma vez por alvo
     * @param nova se alguma linha do grupo ainda não foi lida
     */
    public record Grupo(String chave, Instant em, long total, boolean nova) {
    }

    /** Uma linha de um grupo. As do mesmo grupo têm o mesmo tipo, a mesma referência e o mesmo detalhe. */
    public record Linha(String chave, @Nullable UUID origem, TipoNotificacao tipo, @Nullable UUID referencia,
            @Nullable UUID detalhe, @Nullable String trecho) {
    }

    // ------------------------------------------------------------ escrita

    /**
     * Grava a notificação conforme a {@link Repeticao} do tipo.
     *
     * @return se ela entrou ou voltou ao topo; {@code false} se a pessoa já tinha avisado desse alvo
     */
    public boolean gravar(Nova nova) {
        String sql = switch (nova.tipo().repeticao()) {
            case SEMPRE -> INSERIR;
            case UMA_VEZ -> INSERIR + CONFLITO + " do nothing";
            case RENOVA -> INSERIR + CONFLITO + " do update set criado_em = now(), lida_em = null";
        };
        return jdbc.update(sql, new MapSqlParameterSource("destinatario", nova.destinatario())
                .addValue("origem", nova.origem())
                .addValue("tipo", nova.tipo().name())
                .addValue("referencia", nova.referencia())
                .addValue("detalhe", nova.detalhe())
                .addValue("trecho", nova.trecho())) > 0;
    }

    /** Marca como lidas as notificações da pessoa que chegaram até {@code ate}. */
    public int marcarLidas(UUID destinatario, Instant ate) {
        return jdbc.update("""
                update notificacao set lida_em = now()
                where destinatario_id = :destinatario and lida_em is null and criado_em <= :ate
                """, new MapSqlParameterSource("destinatario", destinatario).addValue("ate", data(ate)));
    }

    /** A notificação ainda não lida de quem desfez o que tinha feito, como descurtir. A já lida fica. */
    public List<UUID> apagarNaoLidaDaReferencia(UUID origem, TipoNotificacao tipo, UUID referencia) {
        return apagar("""
                delete from notificacao
                where origem_id = :origem and tipo = :tipo and referencia_id = :alvo and detalhe_id is null
                  and lida_em is null
                returning destinatario_id
                """, new MapSqlParameterSource("origem", origem).addValue("tipo", tipo.name())
                .addValue("alvo", referencia));
    }

    /** Como {@link #apagarNaoLidaDaReferencia}, para o que fica dentro da referência, como um comentário. */
    public List<UUID> apagarNaoLidaDoDetalhe(UUID origem, TipoNotificacao tipo, UUID detalhe) {
        return apagar("""
                delete from notificacao
                where origem_id = :origem and tipo = :tipo and detalhe_id = :alvo and lida_em is null
                returning destinatario_id
                """, new MapSqlParameterSource("origem", origem).addValue("tipo", tipo.name())
                .addValue("alvo", detalhe));
    }

    /** O pedido de conexão que {@code solicitante} retirou, lido ou não: ele não existe mais. */
    public List<UUID> apagarPedido(UUID destinatario, UUID solicitante) {
        return apagar("""
                delete from notificacao
                where tipo = 'SOLICITACAO_CONEXAO' and destinatario_id = :destinatario and origem_id = :solicitante
                returning destinatario_id
                """, new MapSqlParameterSource("destinatario", destinatario).addValue("solicitante", solicitante));
    }

    /** Tudo o que uma pessoa avisou à outra, nos dois sentidos. */
    public List<UUID> apagarEntre(UUID umaPessoa, UUID outra) {
        return apagar("""
                delete from notificacao
                where (destinatario_id = :uma and origem_id = :outra) or (destinatario_id = :outra and origem_id = :uma)
                returning destinatario_id
                """, new MapSqlParameterSource("uma", umaPessoa).addValue("outra", outra));
    }

    /** As notificações que levam a publicações ou anúncios que saíram do ar. */
    public List<UUID> apagarDasReferencias(Collection<UUID> referencias) {
        if (referencias.isEmpty()) {
            return List.of();
        }
        return apagar("delete from notificacao where referencia_id in (:ids) returning destinatario_id",
                new MapSqlParameterSource("ids", referencias));
    }

    /** As notificações de um comentário ou de uma avaliação que foi apagada. */
    public List<UUID> apagarDoDetalhe(UUID detalhe) {
        return apagar("delete from notificacao where detalhe_id = :id returning destinatario_id",
                new MapSqlParameterSource("id", detalhe));
    }

    // ------------------------------------------------------------ leitura

    /** Quantos grupos têm alguma notificação não lida: o número do contador. */
    public long naoLidas(UUID destinatario) {
        Long total = jdbc.queryForObject("select count(distinct " + CHAVE + ") from notificacao n "
                        + "where n.destinatario_id = :destinatario and n.lida_em is null",
                new MapSqlParameterSource("destinatario", destinatario), Long.class);
        return total != null ? total : 0;
    }

    /**
     * Uma página de grupos, do mais novo para o mais antigo.
     *
     * @param antesEm com {@code antesChave}, onde a página anterior parou; {@code null} na primeira
     */
    public List<Grupo> grupos(UUID destinatario, @Nullable Instant antesEm, @Nullable String antesChave,
            int limite) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("destinatario", destinatario)
                .addValue("limite", limite);
        StringBuilder sql = new StringBuilder("""
                select g.chave, g.em, g.total, g.nova from (
                    select %s as chave, max(n.criado_em) as em, count(*) as total, bool_or(n.lida_em is null) as nova
                    from notificacao n
                    where n.destinatario_id = :destinatario
                    group by 1
                ) g
                """.formatted(CHAVE));
        if (antesEm != null && antesChave != null) {
            sql.append(" where (g.em, g.chave) < (:antesEm, :antesChave)");
            parametros.addValue("antesEm", data(antesEm)).addValue("antesChave", antesChave);
        }
        sql.append(" order by g.em desc, g.chave desc limit :limite");
        return jdbc.query(sql.toString(), parametros, NotificacaoRepository::grupo);
    }

    /** As linhas mais novas de cada grupo, até {@code porGrupo} de cada um, das mais novas para as mais antigas. */
    public List<Linha> linhas(UUID destinatario, Collection<String> chaves, int porGrupo) {
        if (chaves.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select x.chave, x.origem_id, x.tipo, x.referencia_id, x.detalhe_id, x.trecho from (
                    select %1$s as chave, n.origem_id, n.tipo, n.referencia_id, n.detalhe_id, n.trecho,
                           row_number() over (partition by %1$s order by n.criado_em desc, n.id desc) as posicao
                    from notificacao n
                    where n.destinatario_id = :destinatario and %1$s in (:chaves)
                ) x
                where x.posicao <= :porGrupo
                order by x.chave, x.posicao
                """.formatted(CHAVE), new MapSqlParameterSource("destinatario", destinatario)
                .addValue("chaves", chaves).addValue("porGrupo", porGrupo), NotificacaoRepository::linha);
    }

    // ------------------------------------------------------------ apoio

    private List<UUID> apagar(String sql, MapSqlParameterSource parametros) {
        return jdbc.queryForList(sql, parametros, UUID.class).stream().distinct().toList();
    }

    private static String tipos(Predicate<TipoNotificacao> filtro) {
        return Arrays.stream(TipoNotificacao.values()).filter(filtro)
                .map(tipo -> "'" + tipo.name() + "'")
                .collect(Collectors.joining(", "));
    }

    /** O driver do PostgreSQL não aceita {@link Instant} como parâmetro; {@link OffsetDateTime} sim. */
    private static OffsetDateTime data(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Grupo grupo(ResultSet linha, int numero) throws SQLException {
        return new Grupo(linha.getString("chave"), linha.getObject("em", OffsetDateTime.class).toInstant(),
                linha.getLong("total"), linha.getBoolean("nova"));
    }

    private static Linha linha(ResultSet linha, int numero) throws SQLException {
        return new Linha(linha.getString("chave"), linha.getObject("origem_id", UUID.class),
                TipoNotificacao.valueOf(linha.getString("tipo")), linha.getObject("referencia_id", UUID.class),
                linha.getObject("detalhe_id", UUID.class), linha.getString("trecho"));
    }
}
