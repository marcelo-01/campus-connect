package br.com.campusconnect.usuario.service;

/**
 * O refresh token não existe, venceu, foi revogado ou pertence a uma conta que não está mais ativa.
 * O cliente precisa entrar de novo.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException() {
        super("Sua sessão expirou. Entre novamente.");
    }
}
