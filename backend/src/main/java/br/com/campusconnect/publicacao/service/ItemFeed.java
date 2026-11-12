package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Um item de uma página do feed.
 *
 * @param republicadaPor quem republicou, no feed de um perfil; {@code null} quando é o próprio post
 * @param em data que ordena o item: a da publicação, a da republicação ou a de quando foi salva
 */
public record ItemFeed(PublicacaoVista publicacao, @Nullable UsuarioPublico republicadaPor, Instant em) {
}
