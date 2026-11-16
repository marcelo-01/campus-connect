package br.com.campusconnect.marketplace.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que a pessoa escolheu na vitrine. Os campos nulos valem o padrão.
 *
 * @param escopo {@code bolhas} (padrão: as bolhas em que a pessoa está), {@code rede} (todos) ou
 *     {@code favoritos}
 * @param bolhaId só os anúncios direcionados a esta bolha
 * @param tipo {@code SERVICO} ou {@code PRODUTO}
 * @param categoria o slug da categoria
 * @param busca texto procurado no título e na descrição
 * @param ordem {@code recentes} (padrão), {@code preco-asc}, {@code preco-desc} ou {@code avaliacao}
 */
public record FiltroVitrine(@Nullable String escopo, @Nullable UUID bolhaId, @Nullable String tipo,
        @Nullable String categoria, @Nullable String busca, @Nullable String ordem) {
}
