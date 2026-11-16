package br.com.campusconnect.marketplace.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que a pessoa preenche ao anunciar ou ao editar. As fotos vão à parte.
 *
 * @param tipo {@code SERVICO} ou {@code PRODUTO}
 * @param modalidadePreco {@code FIXO}, {@code POR_HORA}, {@code A_COMBINAR} ou {@code GRATUITO}
 * @param preco obrigatório em {@code FIXO} e {@code POR_HORA}, e vazio nas outras modalidades
 * @param bolhaIds de 1 a 5 bolhas de que quem anuncia é membro
 */
public record DadosAnuncio(@Nullable String tipo, @Nullable UUID categoriaId, @Nullable String titulo,
        @Nullable String descricao, @Nullable String modalidadePreco, @Nullable BigDecimal preco,
        @Nullable List<UUID> bolhaIds) {
}
