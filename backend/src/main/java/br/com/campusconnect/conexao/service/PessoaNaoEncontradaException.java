package br.com.campusconnect.conexao.service;

import java.util.UUID;

/** A conta não existe ou não aparece para outras pessoas (suspensa, banida, desativada ou anonimizada). */
public class PessoaNaoEncontradaException extends RuntimeException {

    public PessoaNaoEncontradaException(UUID id) {
        super("Pessoa não encontrada: " + id);
    }
}
