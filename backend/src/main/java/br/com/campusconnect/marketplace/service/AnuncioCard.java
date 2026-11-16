package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Um anúncio na vitrine ou no perfil, já do ponto de vista de quem está vendo.
 *
 * @param tipo {@code SERVICO} ou {@code PRODUTO}
 * @param modalidadePreco {@code FIXO}, {@code POR_HORA}, {@code A_COMBINAR} ou {@code GRATUITO}
 * @param preco só em {@code FIXO} e {@code POR_HORA}
 * @param capaUrl a primeira foto, ou {@code null} para o frontend mostrar o gradiente
 * @param bolhas as bolhas a que o anúncio é direcionado, das mais amplas para as mais específicas; pode
 *     vir vazia se as bolhas dele foram excluídas
 * @param media a média das notas, ou {@code null} se ninguém avaliou
 * @param favorito se quem vê favoritou
 * @param status {@code PUBLICADO} ou, para quem anunciou, {@code PAUSADO}
 */
public record AnuncioCard(UUID id, String tipo, String titulo, CategoriaVista categoria,
        @Nullable String modalidadePreco, @Nullable BigDecimal preco, @Nullable String capaUrl,
        UsuarioPublico anunciante, List<BolhaResumo> bolhas, @Nullable Double media, long totalAvaliacoes,
        boolean favorito, String status) {
}
