package br.com.campusconnect.conversa.repository;

import static br.com.campusconnect.conversa.repository.ConversaRepository.data;
import static br.com.campusconnect.conversa.repository.ConversaRepository.instante;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As mensagens de uma conversa. As datas vêm do relógio do banco, como a leitura de cada participante: com dois
 * relógios, o "Visto" e as não lidas errariam por alguns segundos.
 */
@Repository
@RequiredArgsConstructor
public class MensagemRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param conteudo {@code null} se a mensagem foi apagada */
    public record Linha(UUID id, UUID remetenteId, @Nullable String conteudo, Instant enviadoEm, boolean apagada) {
    }

    /**
     * Grava a mensagem com a hora em que a instrução roda ({@code clock_timestamp()}), e não a do começo da transação
     * ({@code now()}): assim a ordem das datas fica mais perto da ordem real dos envios.
     */
    public Linha inserir(UUID conversaId, UUID remetenteId, String conteudo) {
        return jdbc.queryForObject("""
                insert into mensagem (conversa_id, remetente_id, conteudo, enviado_em)
                values (:conversa, :remetente, :conteudo, clock_timestamp())
                returning id, remetente_id, conteudo, enviado_em, false as apagada
                """, new MapSqlParameterSource("conversa", conversaId)
                .addValue("remetente", remetenteId)
                .addValue("conteudo", conteudo), MensagemRepository::linha);
    }

    /** Uma página da conversa, da mensagem mais nova para a mais antiga. */
    public List<Linha> pagina(UUID conversaId, @Nullable Instant antesDe, @Nullable UUID antesDoId, int limite) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("conversa", conversaId)
                .addValue("limite", limite);
        StringBuilder sql = new StringBuilder("""
                select id, remetente_id, conteudo, enviado_em, removido_em is not null as apagada
                from mensagem where conversa_id = :conversa
                """);
        if (antesDe != null && antesDoId != null) {
            sql.append(" and (enviado_em, id) < (:antesDe, :antesDoId)");
            parametros.addValue("antesDe", data(antesDe)).addValue("antesDoId", antesDoId);
        }
        sql.append(" order by enviado_em desc, id desc limit :limite");
        return jdbc.query(sql.toString(), parametros, MensagemRepository::linha);
    }

    /** A mensagem, só se ela for desta conversa. */
    public Optional<Linha> buscar(UUID conversaId, UUID mensagemId) {
        return jdbc.query("""
                select id, remetente_id, conteudo, enviado_em, removido_em is not null as apagada
                from mensagem where id = :id and conversa_id = :conversa
                """, new MapSqlParameterSource("id", mensagemId).addValue("conversa", conversaId),
                MensagemRepository::linha).stream().findFirst();
    }

    /**
     * Apaga a mensagem para os dois lados: o texto sai do banco. Só vale para quem mandou e só uma vez.
     *
     * @return 1 se apagou, 0 se a mensagem não é de {@code remetenteId} ou já estava apagada
     */
    public int apagar(UUID conversaId, UUID mensagemId, UUID remetenteId) {
        return jdbc.update("""
                update mensagem set conteudo = null, removido_em = clock_timestamp()
                where id = :id and conversa_id = :conversa and remetente_id = :remetente and removido_em is null
                """, new MapSqlParameterSource("id", mensagemId)
                .addValue("conversa", conversaId)
                .addValue("remetente", remetenteId));
    }

    /** Quantas mensagens a pessoa mandou no período que acaba agora, em todas as conversas. */
    public long enviadasNosUltimos(UUID remetenteId, Duration periodo) {
        Long total = jdbc.queryForObject("""
                select count(*) from mensagem
                where remetente_id = :remetente and enviado_em > clock_timestamp() - make_interval(secs => :segundos)
                """, new MapSqlParameterSource("remetente", remetenteId).addValue("segundos", periodo.toSeconds()),
                Long.class);
        return total == null ? 0 : total;
    }

    /** Quantas conversas a pessoa começou no período que acaba agora: aquelas em que a primeira mensagem é dela. */
    public long conversasComecadasNosUltimos(UUID remetenteId, Duration periodo) {
        Long total = jdbc.queryForObject("""
                select count(*) from mensagem m
                where m.remetente_id = :remetente
                  and m.enviado_em > clock_timestamp() - make_interval(secs => :segundos)
                  and not exists (select 1 from mensagem a
                                  where a.conversa_id = m.conversa_id and (a.enviado_em, a.id) < (m.enviado_em, m.id))
                """, new MapSqlParameterSource("remetente", remetenteId).addValue("segundos", periodo.toSeconds()),
                Long.class);
        return total == null ? 0 : total;
    }

    private static Linha linha(ResultSet linha, int numero) throws SQLException {
        return new Linha(linha.getObject("id", UUID.class), linha.getObject("remetente_id", UUID.class),
                linha.getString("conteudo"), instante(linha, "enviado_em"), linha.getBoolean("apagada"));
    }
}
