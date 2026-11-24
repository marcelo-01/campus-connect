package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/auth/alterar-senha}. A senha nova segue a regra do cadastro. */
record AlteracaoSenhaRequest(
        @NotBlank(message = "Informe sua senha atual.")
        String senhaAtual,

        @NotBlank(message = "Crie uma senha nova.")
        @Size(min = 8, max = 128, message = "A senha precisa ter de 8 a 128 caracteres.")
        String novaSenha) {

    @Override
    public String toString() {
        return "AlteracaoSenhaRequest[]";
    }
}
