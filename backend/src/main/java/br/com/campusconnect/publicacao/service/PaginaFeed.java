package br.com.campusconnect.publicacao.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uma página do feed.
 *
 * @param proximo cursor da página seguinte, para mandar de volta como está; {@code null} na última
 * @param topo marca do post mais novo, para perguntar depois se chegaram posts novos. Só vem na primeira
 *     página do Início e da bolha, e nunca no "Em alta", que não é por data
 */
public record PaginaFeed(List<ItemFeed> itens, @Nullable String proximo, @Nullable String topo) {
}
