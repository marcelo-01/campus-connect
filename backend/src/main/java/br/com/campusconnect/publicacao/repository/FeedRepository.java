package br.com.campusconnect.publicacao.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As consultas que montam as páginas dos feeds. Cada uma devolve só a identificação e a data de cada
 * item, na ordem certa; o serviço carrega o resto em lote.
 *
 * <p>A paginação é por <em>keyset</em>: a página seguinte começa depois do último item da anterior, pela
 * data e pelo id. Assim um post novo não empurra os outros para a página seguinte e nada aparece duas
 * vezes. "Em alta" ordena por curtidas, que mudam a todo momento, e por isso usa deslocamento.
 *
 * <p>O SQL é montado só com trechos fixos daqui; o que vem de fora entra sempre como parâmetro.
 */
@Repository
@RequiredArgsConstructor
public class FeedRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Um item do feed.
     *
     * @param republicadaPor quem republicou, no feed de um perfil; {@code null} nos outros casos
     * @param em data que ordena o item: a da publicação, a da republicação ou a de quando foi salva
     */
    public record Item(UUID publicacaoId, @Nullable UUID republicadaPor, Instant em) {
    }

    /** Posts publicados nas bolhas dadas, do mais novo para o mais antigo. */
    public List<Item> dasBolhas(Collection<UUID> bolhas, boolean soComMidia, @Nullable Instant antesDe,
            @Nullable UUID antesDoId, int limite) {
        if (bolhas.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                select p.id, null::uuid as republicada_por, p.criado_em as em
                from publicacao p
                where p.status = 'PUBLICADA' and p.bolha_id in (:bolhas)
                """);
        if (soComMidia) {
            sql.append("""
                    and exists (select 1 from midia_publicacao m
                                where m.publicacao_id = p.id and m.tipo in ('IMAGEM', 'VIDEO'))
                    """);
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("bolhas", bolhas);
        depoisDe(sql, parametros, "(p.criado_em, p.id)", antesDe, antesDoId);
        sql.append(" order by p.criado_em desc, p.id desc limit :limite");
        parametros.addValue("limite", limite);
        return jdbc.query(sql.toString(), parametros, FeedRepository::item);
    }

    /**
     * Posts das bolhas dadas publicados desde {@code desde}, dos mais curtidos para os menos; no empate,
     * o mais novo primeiro.
     */
    public List<Item> dasBolhasEmAlta(Collection<UUID> bolhas, Instant desde, int deslocamento, int limite) {
        if (bolhas.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select p.id, null::uuid as republicada_por, p.criado_em as em
                from publicacao p
                left join (select publicacao_id, count(*) as total from reacao
                           where publicacao_id is not null group by publicacao_id) r
                       on r.publicacao_id = p.id
                where p.status = 'PUBLICADA' and p.bolha_id in (:bolhas) and p.criado_em >= :desde
                order by coalesce(r.total, 0) desc, p.criado_em desc, p.id desc
                offset :deslocamento limit :limite
                """,
                new MapSqlParameterSource("bolhas", bolhas)
                        .addValue("desde", data(desde))
                        .addValue("deslocamento", deslocamento)
                        .addValue("limite", limite),
                FeedRepository::item);
    }

    /**
     * O que aparece no perfil: os posts da pessoa e os que ela republicou, pela data de cada um. Ninguém
     * republica o próprio post, então a mesma publicação não aparece duas vezes.
     */
    public List<Item> doUsuario(UUID usuarioId, @Nullable Instant antesDe, @Nullable UUID antesDoId, int limite) {
        StringBuilder sql = new StringBuilder("""
                select publicacao_id as id, republicada_por, em from (
                    select p.id as publicacao_id, null::uuid as republicada_por, p.criado_em as em
                    from publicacao p
                    where p.autor_id = :usuario and p.status = 'PUBLICADA'
                  union all
                    select r.publicacao_id, r.usuario_id, r.criado_em
                    from republicacao r join publicacao p on p.id = r.publicacao_id
                    where r.usuario_id = :usuario and p.status = 'PUBLICADA'
                ) itens
                where true
                """);
        MapSqlParameterSource parametros = new MapSqlParameterSource("usuario", usuarioId);
        depoisDe(sql, parametros, "(em, publicacao_id)", antesDe, antesDoId);
        sql.append(" order by em desc, publicacao_id desc limit :limite");
        parametros.addValue("limite", limite);
        return jdbc.query(sql.toString(), parametros, FeedRepository::item);
    }

    /** Posts que a pessoa salvou, do salvo por último para o primeiro. */
    public List<Item> salvasPor(UUID usuarioId, @Nullable Instant antesDe, @Nullable UUID antesDoId, int limite) {
        StringBuilder sql = new StringBuilder("""
                select p.id, null::uuid as republicada_por, s.salva_em as em
                from publicacao_salva s join publicacao p on p.id = s.publicacao_id
                where s.usuario_id = :usuario and p.status = 'PUBLICADA'
                """);
        MapSqlParameterSource parametros = new MapSqlParameterSource("usuario", usuarioId);
        depoisDe(sql, parametros, "(s.salva_em, p.id)", antesDe, antesDoId);
        sql.append(" order by s.salva_em desc, p.id desc limit :limite");
        parametros.addValue("limite", limite);
        return jdbc.query(sql.toString(), parametros, FeedRepository::item);
    }

    /** Continua depois do último item da página anterior, quando houver um. */
    private static void depoisDe(StringBuilder sql, MapSqlParameterSource parametros, String chave,
            @Nullable Instant antesDe, @Nullable UUID antesDoId) {
        if (antesDe != null && antesDoId != null) {
            sql.append(" and ").append(chave).append(" < (:antesDe, :antesDoId)");
            parametros.addValue("antesDe", data(antesDe)).addValue("antesDoId", antesDoId);
        }
    }

    /** O driver do PostgreSQL não aceita {@link Instant} como parâmetro; {@link OffsetDateTime} sim. */
    private static OffsetDateTime data(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Item item(ResultSet linha, int numero) throws SQLException {
        return new Item(linha.getObject("id", UUID.class), linha.getObject("republicada_por", UUID.class),
                linha.getObject("em", OffsetDateTime.class).toInstant());
    }
}
