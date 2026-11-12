package br.com.campusconnect.publicacao.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O texto e o destino de uma publicação nova. As imagens e o vídeo vão à parte.
 *
 * @param link endereço web anexado; sem esquema, vale {@code https://}
 */
public record NovaPublicacao(UUID bolhaId, @Nullable String conteudo, @Nullable String link) {
}
