package br.com.campusconnect.marketplace.service;

import java.util.UUID;

/**
 * Uma categoria do marketplace.
 *
 * @param tipo {@code SERVICO} ou {@code PRODUTO}: um anúncio só usa categorias do próprio tipo
 */
public record CategoriaVista(UUID id, String nome, String slug, String tipo) {
}
