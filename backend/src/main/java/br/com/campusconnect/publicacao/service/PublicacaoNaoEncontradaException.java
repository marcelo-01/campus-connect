package br.com.campusconnect.publicacao.service;

import java.util.UUID;

/** A publicação não existe ou foi excluída. A API responde 404. */
public class PublicacaoNaoEncontradaException extends RuntimeException {

    public PublicacaoNaoEncontradaException(UUID id) {
        super("Publicação " + id + " não encontrada.");
    }
}
