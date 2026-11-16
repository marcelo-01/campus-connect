package br.com.campusconnect.marketplace.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uma página da vitrine ou dos anúncios de um perfil.
 *
 * @param proximo cursor da página seguinte, para mandar de volta como está; {@code null} na última
 * @param total quantos anúncios passam no filtro da vitrine. Só vem na primeira página da vitrine
 */
public record PaginaAnuncios(List<AnuncioCard> itens, @Nullable String proximo, @Nullable Long total) {
}
