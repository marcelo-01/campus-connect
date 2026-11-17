package br.com.campusconnect.conversa.service;

import java.util.UUID;

/**
 * A conversa não existe, quem pediu não participa dela, ou a outra pessoa não aparece mais (conta suspensa, banida,
 * desativada ou anonimizada). Os três casos dão a mesma resposta, 404, para não revelar que a conversa existe.
 */
public class ConversaNaoEncontradaException extends RuntimeException {

    public ConversaNaoEncontradaException(UUID id) {
        super("Conversa não encontrada: " + id);
    }
}
