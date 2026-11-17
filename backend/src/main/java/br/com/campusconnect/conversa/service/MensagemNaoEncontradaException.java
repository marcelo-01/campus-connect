package br.com.campusconnect.conversa.service;

import java.util.UUID;

/** A mensagem não existe nesta conversa. A API responde 404. */
public class MensagemNaoEncontradaException extends RuntimeException {

    public MensagemNaoEncontradaException(UUID id) {
        super("Mensagem não encontrada: " + id);
    }
}
