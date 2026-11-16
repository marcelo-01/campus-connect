package br.com.campusconnect.marketplace.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record AvaliacaoRequest(
        @NotNull(message = "Dê uma nota de 1 a 5.")
        @Min(value = 1, message = "Dê uma nota de 1 a 5.")
        @Max(value = 5, message = "Dê uma nota de 1 a 5.")
        Integer nota,

        @Size(max = 500, message = "O comentário pode ter até 500 caracteres.")
        String comentario) {

    AvaliacaoRequest {
        comentario = comentario == null ? null : comentario.strip();
    }
}
