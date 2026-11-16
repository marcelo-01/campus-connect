package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A avaliação de um anúncio.
 *
 * @param nota de 1 a 5
 * @param podeExcluir se quem vê escreveu a avaliação ou é administrador
 */
public record AvaliacaoVista(UUID id, UsuarioPublico autor, int nota, @Nullable String comentario,
        Instant criadoEm, boolean podeExcluir) {
}
