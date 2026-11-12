package br.com.campusconnect.publicacao.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uma página do feed.
 *
 * @param proximo cursor da página seguinte, para mandar de volta como está; {@code null} na última
 */
public record PaginaFeed(List<ItemFeed> itens, @Nullable String proximo) {
}
