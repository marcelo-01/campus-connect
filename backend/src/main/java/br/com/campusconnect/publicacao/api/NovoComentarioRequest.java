package br.com.campusconnect.publicacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @param respondendoA comentário a que este responde, ou {@code null} para um comentário novo */
record NovoComentarioRequest(
        @NotBlank(message = "Escreva o comentário.")
        @Size(max = 1000, message = "O comentário pode ter até 1000 caracteres.")
        String conteudo,

        UUID respondendoA) {

    NovoComentarioRequest {
        conteudo = conteudo == null ? null : conteudo.strip();
    }
}
