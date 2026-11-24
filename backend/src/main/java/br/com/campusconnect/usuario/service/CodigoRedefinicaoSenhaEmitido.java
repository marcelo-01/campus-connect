package br.com.campusconnect.usuario.service;

import java.time.Duration;

/**
 * Alguém pediu para redefinir a senha da conta, e o código acabou de ser gravado. Quem manda o e-mail é o módulo
 * {@code email}, depois do commit.
 *
 * <p>Como no {@link CodigoVerificacaoEmitido}, o código vai puro e o evento só vive na memória.
 *
 * @param nomeCompleto o nome da conta, para a saudação
 * @param validade quanto tempo o código vale, para o texto do e-mail
 */
public record CodigoRedefinicaoSenhaEmitido(String email, String nomeCompleto, String codigo, Duration validade) {

    @Override
    public String toString() {
        return "CodigoRedefinicaoSenhaEmitido[email=" + email + ", validade=" + validade + "]";
    }
}
