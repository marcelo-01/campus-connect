package br.com.campusconnect.conversa.service;

import java.util.UUID;

/** O dado do evento ao vivo {@code mensagem-apagada}. */
public record EventoMensagemApagada(UUID conversaId, UUID mensagemId) {
}
