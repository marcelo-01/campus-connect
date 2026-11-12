package br.com.campusconnect.publicacao.api;

import br.com.campusconnect.publicacao.service.NovaPublicacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A parte {@code dados} do multipart de uma publicação nova. As imagens e o vídeo vêm em partes próprias. */
record NovaPublicacaoRequest(
        @NotNull(message = "Escolha a bolha da publicação.")
        UUID bolhaId,

        @Size(max = 3000, message = "A publicação pode ter até 3000 caracteres.")
        String conteudo,

        @Size(max = 2000, message = "O link pode ter até 2000 caracteres.")
        String link) {

    NovaPublicacaoRequest {
        conteudo = conteudo == null ? null : conteudo.strip();
        link = link == null ? null : link.strip();
    }

    NovaPublicacao paraNovaPublicacao() {
        return new NovaPublicacao(bolhaId, conteudo, link);
    }
}
