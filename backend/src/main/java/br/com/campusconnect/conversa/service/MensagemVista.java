package br.com.campusconnect.conversa.service;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uma mensagem como as duas pessoas a veem. É também o dado do evento ao vivo {@code mensagem}, que vai igual para os
 * dois lados: o cliente sabe se a mensagem é dele pelo {@code remetenteId}.
 *
 * @param conteudo o texto; {@code null} se a mensagem foi apagada
 */
public record MensagemVista(UUID id, UUID conversaId, UUID remetenteId, @Nullable String conteudo, Instant enviadoEm,
        boolean apagada) {
}
