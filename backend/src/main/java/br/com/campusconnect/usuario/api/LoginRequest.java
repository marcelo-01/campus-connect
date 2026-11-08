package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /api/auth/login}. Sem {@code manterConectado}, vale {@code false}. */
record LoginRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email,

        @NotBlank(message = "Informe sua senha.")
        String senha,

        boolean manterConectado) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", manterConectado=" + manterConectado + "]";
    }
}
