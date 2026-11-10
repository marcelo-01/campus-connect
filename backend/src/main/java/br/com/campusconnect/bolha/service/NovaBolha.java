package br.com.campusconnect.bolha.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dados para criar uma bolha.
 *
 * @param tipo {@code GERAL}, {@code CAMPUS}, {@code CURSO} ou {@code COMUNIDADE}
 * @param bolhaPaiId bolha acima, ou {@code null} para ficar no topo
 */
public record NovaBolha(String nome, @Nullable String sigla, String tipo, @Nullable UUID bolhaPaiId,
        @Nullable String descricao, @Nullable String endereco) {
}
