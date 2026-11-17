package br.com.campusconnect.conversa.service;

import java.util.UUID;

/** A conta não existe ou não aparece para outras pessoas. A API responde 404. */
public class PessoaIndisponivelException extends RuntimeException {

    public PessoaIndisponivelException(UUID id) {
        super("Pessoa não encontrada: " + id);
    }
}
