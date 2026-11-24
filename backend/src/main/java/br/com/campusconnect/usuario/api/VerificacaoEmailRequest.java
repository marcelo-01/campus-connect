package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de {@code POST /api/auth/verificar-email}. O construtor tira os espaços e os hífens do código, que podem vir
 * colados do e-mail.
 */
record VerificacaoEmailRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email,

        @NotBlank(message = "Digite o código que mandamos para o seu e-mail.")
        @Pattern(regexp = "^\\d{6}$", message = "O código tem 6 números.")
        String codigo) {

    VerificacaoEmailRequest {
        codigo = codigo == null ? null : codigo.replaceAll("[\\s-]", "");
    }

    @Override
    public String toString() {
        return "VerificacaoEmailRequest[email=" + email + "]";
    }
}
