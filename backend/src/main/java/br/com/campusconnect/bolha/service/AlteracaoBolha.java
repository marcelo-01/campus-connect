package br.com.campusconnect.bolha.service;

import org.jspecify.annotations.Nullable;

/** O que dá para mudar numa bolha depois de criada. Tipo, bolha acima e slug ficam como estão. */
public record AlteracaoBolha(String nome, @Nullable String sigla, @Nullable String descricao,
        @Nullable String endereco) {
}
