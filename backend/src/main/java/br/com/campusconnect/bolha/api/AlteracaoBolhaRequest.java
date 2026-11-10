package br.com.campusconnect.bolha.api;

import br.com.campusconnect.bolha.service.AlteracaoBolha;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT /api/bolhas/{id}}. Tipo e bolha acima não mudam depois de criados. */
record AlteracaoBolhaRequest(
        @NotBlank(message = "Dê um nome à bolha.")
        @Size(max = 120, message = "Use no máximo 120 caracteres.")
        String nome,

        @Size(max = 10, message = "Use no máximo 10 caracteres.")
        String sigla,

        @Size(max = 500, message = "A descrição pode ter até 500 caracteres.")
        String descricao,

        @Size(max = 200, message = "Use no máximo 200 caracteres.")
        String endereco) {

    AlteracaoBolhaRequest {
        nome = nome == null ? null : nome.strip();
        sigla = sigla == null ? null : sigla.strip();
        descricao = descricao == null ? null : descricao.strip();
        endereco = endereco == null ? null : endereco.strip();
    }

    AlteracaoBolha paraAlteracao() {
        return new AlteracaoBolha(nome, sigla, descricao, endereco);
    }
}
