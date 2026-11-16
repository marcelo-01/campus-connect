package br.com.campusconnect.marketplace.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As bolhas a que cada anúncio é direcionado. Quando uma bolha é excluída, o vínculo sai junto pela chave
 * estrangeira, e o anúncio continua existindo.
 */
@Repository
@RequiredArgsConstructor
public class AnuncioBolhaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Deixa o anúncio direcionado só a estas bolhas: tira as que saíram e acrescenta as novas, sem mexer
     * nas que continuam. Com JPA, apagar e gravar a mesma chave na mesma transação bateria na chave
     * primária, porque o Hibernate grava antes de apagar.
     *
     * @param bolhas pelo menos uma
     */
    public void vincular(UUID anuncioId, Set<UUID> bolhas) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("anuncio", anuncioId).addValue("bolhas", bolhas);
        jdbc.update("delete from anuncio_bolha where anuncio_id = :anuncio and bolha_id not in (:bolhas)", parametros);
        for (UUID bolha : bolhas) {
            jdbc.update("""
                    insert into anuncio_bolha (anuncio_id, bolha_id) values (:anuncio, :bolha)
                    on conflict do nothing
                    """, new MapSqlParameterSource("anuncio", anuncioId).addValue("bolha", bolha));
        }
    }

    /** As bolhas de cada anúncio, em lote. Um anúncio sem bolha não aparece no mapa. */
    public Map<UUID, List<UUID>> bolhasDe(Collection<UUID> anuncios) {
        Map<UUID, List<UUID>> porAnuncio = new HashMap<>();
        if (!anuncios.isEmpty()) {
            jdbc.query("select anuncio_id, bolha_id from anuncio_bolha where anuncio_id in (:ids)",
                    new MapSqlParameterSource("ids", anuncios),
                    linha -> {
                        porAnuncio.computeIfAbsent(linha.getObject("anuncio_id", UUID.class), id -> new ArrayList<>())
                                .add(linha.getObject("bolha_id", UUID.class));
                    });
        }
        return porAnuncio;
    }
}
