package br.com.campusconnect.marketplace.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As consultas que montam a vitrine do marketplace e a lista de anúncios de um perfil. Devolvem só os
 * ids, na ordem certa; o serviço carrega o resto em lote.
 *
 * <p>A vitrine pode ser ordenada por preço e por avaliação, que não servem de chave estável, então a
 * paginação é por deslocamento.
 *
 * <p>O SQL é montado só com trechos fixos daqui; o que vem de fora entra sempre como parâmetro.
 */
@Repository
@RequiredArgsConstructor
public class VitrineRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** As ordens da vitrine. O desempate é sempre o anúncio mais novo primeiro. */
    public enum Ordem {
        RECENTES("a.publicado_em desc, a.id desc"),
        // "A combinar" fica no fim nos dois sentidos; "Grátis" conta como zero.
        PRECO_ASC("case when a.modalidade_preco = 'A_COMBINAR' then 1 else 0 end, coalesce(a.preco, 0) asc,"
                + " a.publicado_em desc, a.id desc"),
        PRECO_DESC("case when a.modalidade_preco = 'A_COMBINAR' then 1 else 0 end, coalesce(a.preco, 0) desc,"
                + " a.publicado_em desc, a.id desc"),
        // Sem avaliação vai para o fim, e não para o começo, como o "desc" do PostgreSQL faria.
        AVALIACAO("r.media desc nulls last, r.total desc nulls last, a.publicado_em desc, a.id desc");

        private final String sql;

        Ordem(String sql) {
            this.sql = sql;
        }
    }

    /**
     * O que filtra a vitrine. Os campos nulos não filtram.
     *
     * @param bolhas só os anúncios direcionados a alguma destas bolhas; vazio não encontra nada
     * @param favoritosDe só os favoritos desta pessoa
     * @param tipo {@code SERVICO} ou {@code PRODUTO}
     * @param categoria o slug da categoria
     * @param busca texto procurado no título e na descrição, sem os curingas do {@code like}
     */
    public record Filtro(@Nullable Collection<UUID> bolhas, @Nullable UUID favoritosDe, @Nullable String tipo,
            @Nullable String categoria, @Nullable String busca) {
    }

    /** Anúncios publicados que passam no filtro, na ordem pedida. */
    public List<UUID> buscar(Filtro filtro, Ordem ordem, int deslocamento, int limite) {
        if (filtro.bolhas() != null && filtro.bolhas().isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("select a.id from anuncio a");
        if (ordem == Ordem.AVALIACAO) {
            sql.append(" left join (select anuncio_id, avg(nota) as media, count(*) as total")
                    .append(" from avaliacao_anuncio group by anuncio_id) r on r.anuncio_id = a.id");
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        onde(sql, parametros, filtro);
        sql.append(" order by ").append(ordem.sql).append(" offset :deslocamento limit :limite");
        parametros.addValue("deslocamento", deslocamento).addValue("limite", limite);
        return jdbc.queryForList(sql.toString(), parametros, UUID.class);
    }

    /** Quantos anúncios publicados passam no filtro, para a contagem no topo da vitrine. */
    public long contar(Filtro filtro) {
        if (filtro.bolhas() != null && filtro.bolhas().isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("select count(*) from anuncio a");
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        onde(sql, parametros, filtro);
        Long total = jdbc.queryForObject(sql.toString(), parametros, Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Os anúncios de uma pessoa, dos mais novos para os mais antigos.
     *
     * @param comPausados quando quem vê é a própria pessoa, que também vê os pausados
     */
    public List<UUID> doAnunciante(UUID anuncianteId, boolean comPausados, int deslocamento, int limite) {
        return jdbc.queryForList("""
                select a.id from anuncio a
                where a.anunciante_id = :anunciante and a.status in (:status)
                order by a.publicado_em desc nulls last, a.id desc
                offset :deslocamento limit :limite
                """, new MapSqlParameterSource("anunciante", anuncianteId)
                .addValue("status", comPausados ? List.of("PUBLICADO", "PAUSADO") : List.of("PUBLICADO"))
                .addValue("deslocamento", deslocamento)
                .addValue("limite", limite), UUID.class);
    }

    private static void onde(StringBuilder sql, MapSqlParameterSource parametros, Filtro filtro) {
        sql.append(" where a.status = 'PUBLICADO'");
        if (filtro.bolhas() != null) {
            sql.append(" and exists (select 1 from anuncio_bolha ab")
                    .append(" where ab.anuncio_id = a.id and ab.bolha_id in (:bolhas))");
            parametros.addValue("bolhas", filtro.bolhas());
        }
        if (filtro.favoritosDe() != null) {
            sql.append(" and exists (select 1 from favorito_anuncio f")
                    .append(" where f.anuncio_id = a.id and f.usuario_id = :favoritosDe)");
            parametros.addValue("favoritosDe", filtro.favoritosDe());
        }
        if (filtro.tipo() != null) {
            sql.append(" and a.tipo = :tipo");
            parametros.addValue("tipo", filtro.tipo());
        }
        if (filtro.categoria() != null) {
            sql.append(" and exists (select 1 from categoria_anuncio c")
                    .append(" where c.id = a.categoria_id and c.slug = :categoria)");
            parametros.addValue("categoria", filtro.categoria());
        }
        if (filtro.busca() != null) {
            sql.append(" and (a.titulo ilike :busca escape '\\' or a.descricao ilike :busca escape '\\')");
            parametros.addValue("busca", "%" + semCuringas(filtro.busca()) + "%");
        }
    }

    /** O {@code %} e o {@code _} digitados valem como texto, e não como curingas do {@code like}. */
    static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
