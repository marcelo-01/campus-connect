package br.com.campusconnect.publicacao.service;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O texto e o destino de uma publicação nova. As imagens e o vídeo vão à parte.
 *
 * @param bolhaIds as bolhas em que o post aparece, todas de que quem publica é membro
 * @param link endereço web anexado; sem esquema, vale {@code https://}
 */
public record NovaPublicacao(@Nullable List<UUID> bolhaIds, @Nullable String conteudo, @Nullable String link) {
}
