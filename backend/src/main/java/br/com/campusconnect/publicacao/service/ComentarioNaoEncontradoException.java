package br.com.campusconnect.publicacao.service;

import java.util.UUID;

/** O comentário não existe, foi excluído ou é de outra publicação. A API responde 404. */
public class ComentarioNaoEncontradoException extends RuntimeException {

    public ComentarioNaoEncontradoException(UUID id) {
        super("Comentário " + id + " não encontrado.");
    }
}
