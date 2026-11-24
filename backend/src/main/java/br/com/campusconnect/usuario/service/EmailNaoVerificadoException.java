package br.com.campusconnect.usuario.service;

import org.springframework.security.authentication.AccountStatusException;

/**
 * A senha está certa, mas a conta ainda não verificou o e-mail. Só aparece depois da senha, como as outras situações
 * da conta: sem a senha, ninguém descobre que a conta existe e está pendente.
 */
public class EmailNaoVerificadoException extends AccountStatusException {

    public EmailNaoVerificadoException() {
        super("Confirme o seu e-mail para entrar. Mandamos um código para ele.");
    }
}
