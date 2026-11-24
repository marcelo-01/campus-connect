package br.com.campusconnect.usuario.service;

import java.time.Duration;

/**
 * Um código de verificação acabou de ser gravado e precisa ir para o e-mail da conta. Quem manda o e-mail é o módulo
 * {@code email}, depois do commit.
 *
 * <p>O código vai puro, porque o e-mail precisa dele. O evento não é gravado em lugar nenhum e só vive na memória.
 *
 * @param nomeCompleto o nome da conta, para a saudação
 * @param validade quanto tempo o código vale, para o texto do e-mail
 */
public record CodigoVerificacaoEmitido(String email, String nomeCompleto, String codigo, Duration validade) {

    @Override
    public String toString() {
        return "CodigoVerificacaoEmitido[email=" + email + ", validade=" + validade + "]";
    }
}
