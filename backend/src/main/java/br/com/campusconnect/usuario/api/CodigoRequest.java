package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo das rotas que conferem o código mandado para o e-mail: {@code POST /api/auth/verificar-email} e
 * {@code POST /api/auth/redefinir-senha/conferir}. O construtor tira os espaços e os hífens do código, que podem vir
 * colados do e-mail.
 */
record CodigoRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email,

        @NotBlank(message = "Digite o código que mandamos para o seu e-mail.")
        @Pattern(regexp = "^\\d{6}$", message = "O código tem 6 números.")
        String codigo) {

    CodigoRequest {
        codigo = semSeparadores(codigo);
    }

    /** Também usado por {@link RedefinicaoSenhaRequest}. */
    static String semSeparadores(String codigo) {
        return codigo == null ? null : codigo.replaceAll("[\\s-]", "");
    }

    @Override
    public String toString() {
        return "CodigoRequest[email=" + email + "]";
    }
}
