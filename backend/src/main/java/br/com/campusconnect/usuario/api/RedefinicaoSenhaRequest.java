package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/redefinir-senha}. O código é o mesmo já conferido em
 * {@code /redefinir-senha/conferir}, e a senha segue a regra do cadastro.
 */
record RedefinicaoSenhaRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email,

        @NotBlank(message = "Digite o código que mandamos para o seu e-mail.")
        @Pattern(regexp = "^\\d{6}$", message = "O código tem 6 números.")
        String codigo,

        @NotBlank(message = "Crie uma senha nova.")
        @Size(min = 8, max = 128, message = "A senha precisa ter de 8 a 128 caracteres.")
        String novaSenha) {

    RedefinicaoSenhaRequest {
        codigo = CodigoRequest.semSeparadores(codigo);
    }

    @Override
    public String toString() {
        return "RedefinicaoSenhaRequest[email=" + email + "]";
    }
}
