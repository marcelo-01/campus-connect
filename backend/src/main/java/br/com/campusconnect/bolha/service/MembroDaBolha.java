package br.com.campusconnect.bolha.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma pessoa na lista de membros. Só dados públicos: nada de e-mail.
 *
 * @param id id do usuário
 * @param papel {@code MEMBRO}, {@code MODERADOR} ou {@code ADMIN}
 */
public record MembroDaBolha(UUID id, String username, String nomeCompleto, String fotoUrl, String papel,
        Instant entrouEm) {
}
