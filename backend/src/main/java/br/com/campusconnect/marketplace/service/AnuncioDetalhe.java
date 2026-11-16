package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A página de um anúncio: o mesmo que o {@link AnuncioCard} e mais a descrição, as fotos e o que quem
 * vê pode fazer.
 *
 * @param imagens as fotos na ordem; a primeira é a capa
 * @param minhaAvaliacao a avaliação de quem vê, ou {@code null} se ainda não avaliou
 * @param podeEditar se quem vê anunciou: só essa pessoa edita, pausa e reativa
 * @param podeExcluir se quem vê anunciou ou é administrador
 * @param podeAvaliar se o anúncio está publicado e quem vê não é quem anunciou
 */
public record AnuncioDetalhe(UUID id, String tipo, String titulo, CategoriaVista categoria,
        @Nullable String modalidadePreco, @Nullable BigDecimal preco, @Nullable String capaUrl,
        UsuarioPublico anunciante, List<BolhaResumo> bolhas, @Nullable Double media, long totalAvaliacoes,
        boolean favorito, String status, @Nullable String descricao, List<String> imagens,
        @Nullable Instant publicadoEm, @Nullable AvaliacaoVista minhaAvaliacao, boolean podeEditar,
        boolean podeExcluir, boolean podeAvaliar) {
}
