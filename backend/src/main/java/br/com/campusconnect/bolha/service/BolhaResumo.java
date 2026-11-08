package br.com.campusconnect.bolha.service;

import java.util.UUID;

/**
 * Bolha vista de fora do módulo.
 *
 * @param tipo {@code INSTITUICAO}, {@code CAMPUS}, {@code CURSO} ou {@code COMUNIDADE}
 */
public record BolhaResumo(UUID id, String nome, String slug, String tipo, long totalMembros) {
}
