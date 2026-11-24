package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo das rotas que só recebem o e-mail: {@code POST /api/auth/reenviar-codigo} e
 * {@code POST /api/auth/esqueci-senha}.
 */
record EmailRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email) {
}
