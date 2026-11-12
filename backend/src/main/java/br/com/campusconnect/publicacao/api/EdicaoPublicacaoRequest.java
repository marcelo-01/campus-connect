package br.com.campusconnect.publicacao.api;

import jakarta.validation.constraints.Size;

record EdicaoPublicacaoRequest(
        @Size(max = 3000, message = "A publicação pode ter até 3000 caracteres.")
        String conteudo) {

    EdicaoPublicacaoRequest {
        conteudo = conteudo == null ? null : conteudo.strip();
    }
}
