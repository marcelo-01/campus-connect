package br.com.campusconnect.marketplace.service;

import java.util.UUID;

/** O anúncio não existe, foi excluído ou está pausado e quem pede não é quem anunciou. */
public class AnuncioNaoEncontradoException extends RuntimeException {

    public AnuncioNaoEncontradoException(UUID id) {
        super("Anúncio não encontrado: " + id);
    }
}
