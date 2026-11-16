package br.com.campusconnect.marketplace.service;

import java.util.UUID;

public class AvaliacaoNaoEncontradaException extends RuntimeException {

    public AvaliacaoNaoEncontradaException(UUID id) {
        super("Avaliação não encontrada: " + id);
    }
}
