package br.com.campusconnect.usuario.service;

import java.util.UUID;

/** A conta do JWT não existe mais, por exemplo porque foi apagada depois do login. */
public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(UUID id) {
        super("Usuário " + id + " não encontrado");
    }
}
