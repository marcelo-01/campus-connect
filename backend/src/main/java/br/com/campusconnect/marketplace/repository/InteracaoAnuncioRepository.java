package br.com.campusconnect.marketplace.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Favoritos e avaliações dos anúncios. Ligar e desligar são idempotentes: dois cliques seguidos não dão
 * erro nem contam duas vezes.
 *
 * <p>As notas e os favoritos de quem vê são lidos em lote, para a página inteira da vitrine de uma vez.
 */
@Repository
@RequiredArgsConstructor
public class InteracaoAnuncioRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** A média das notas de um anúncio e quantas avaliações ele tem. */
    public record Nota(double media, long total) {
    }

    // ------------------------------------------------------------ favoritos

    public void favoritar(UUID usuarioId, UUID anuncioId) {
        jdbc.update("""
                insert into favorito_anuncio (usuario_id, anuncio_id) values (:usuario, :anuncio)
                on conflict do nothing
                """, parametros(usuarioId, anuncioId));
    }

    public void desfavoritar(UUID usuarioId, UUID anuncioId) {
        jdbc.update("delete from favorito_anuncio where usuario_id = :usuario and anuncio_id = :anuncio",
                parametros(usuarioId, anuncioId));
    }

    /** Quais destes anúncios a pessoa favoritou. */
    public Set<UUID> favoritosDentre(UUID usuarioId, Collection<UUID> anuncios) {
        if (anuncios.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select anuncio_id from favorito_anuncio where usuario_id = :usuario and anuncio_id in (:ids)",
                new MapSqlParameterSource("usuario", usuarioId).addValue("ids", anuncios), UUID.class));
    }

    // ------------------------------------------------------------ avaliações

    /**
     * Grava a avaliação da pessoa, ou troca a nota e o comentário se ela já tinha avaliado. Num comando só,
     * para dois cliques seguidos não baterem na restrição de uma avaliação por pessoa.
     */
    public void avaliar(UUID autorId, UUID anuncioId, short nota, @Nullable String comentario) {
        jdbc.update("""
                insert into avaliacao_anuncio (anuncio_id, autor_id, nota, comentario)
                values (:anuncio, :usuario, :nota, :comentario)
                on conflict (anuncio_id, autor_id)
                do update set nota = excluded.nota, comentario = excluded.comentario
                """, parametros(autorId, anuncioId).addValue("nota", nota).addValue("comentario", comentario));
    }

    /** A média e o total de avaliações de cada anúncio. Os que não têm avaliação não aparecem no mapa. */
    public Map<UUID, Nota> notas(Collection<UUID> anuncios) {
        Map<UUID, Nota> notas = new HashMap<>();
        if (!anuncios.isEmpty()) {
            jdbc.query("""
                    select anuncio_id, avg(nota)::float8 as media, count(*) as total
                    from avaliacao_anuncio where anuncio_id in (:ids) group by anuncio_id
                    """, new MapSqlParameterSource("ids", anuncios),
                    linha -> {
                        notas.put(linha.getObject("anuncio_id", UUID.class),
                                new Nota(linha.getDouble("media"), linha.getLong("total")));
                    });
        }
        return notas;
    }

    private static MapSqlParameterSource parametros(UUID usuarioId, UUID anuncioId) {
        return new MapSqlParameterSource("usuario", usuarioId).addValue("anuncio", anuncioId);
    }
}
