package br.com.campusconnect.usuario.api;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /api/auth/reenviar-codigo}. */
record ReenvioCodigoRequest(
        @NotBlank(message = "Informe seu e-mail.")
        String email) {
}
