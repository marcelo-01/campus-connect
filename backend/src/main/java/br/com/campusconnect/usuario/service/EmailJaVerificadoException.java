package br.com.campusconnect.usuario.service;

/** A conta já verificou o e-mail, talvez em outra aba. Agora ela entra pela senha, e não pelo código. */
public class EmailJaVerificadoException extends RuntimeException {

    public EmailJaVerificadoException() {
        super("Este e-mail já foi confirmado. Entre com a sua senha.");
    }
}
