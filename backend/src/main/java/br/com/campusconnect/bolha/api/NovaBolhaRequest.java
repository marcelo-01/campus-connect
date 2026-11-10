package br.com.campusconnect.bolha.api;

import br.com.campusconnect.bolha.service.NovaBolha;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de {@code POST /api/bolhas}. Tipo e bolha acima são conferidos em {@code BolhaService}, que
 * conhece a hierarquia.
 */
record NovaBolhaRequest(
        @NotBlank(message = "Dê um nome à bolha.")
        @Size(max = 120, message = "Use no máximo 120 caracteres.")
        String nome,

        @Size(max = 10, message = "Use no máximo 10 caracteres.")
        String sigla,

        @NotBlank(message = "Escolha o tipo da bolha.")
        String tipo,

        UUID bolhaPaiId,

        @Size(max = 500, message = "A descrição pode ter até 500 caracteres.")
        String descricao,

        @Size(max = 200, message = "Use no máximo 200 caracteres.")
        String endereco) {

    NovaBolhaRequest {
        nome = nome == null ? null : nome.strip();
        sigla = sigla == null ? null : sigla.strip();
        descricao = descricao == null ? null : descricao.strip();
        endereco = endereco == null ? null : endereco.strip();
    }

    NovaBolha paraNovaBolha() {
        return new NovaBolha(nome, sigla, tipo, bolhaPaiId, descricao, endereco);
    }
}
