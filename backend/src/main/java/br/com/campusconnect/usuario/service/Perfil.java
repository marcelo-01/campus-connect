package br.com.campusconnect.usuario.service;

import br.com.campusconnect.usuario.model.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perfil público de uma conta. Não traz e-mail: o perfil é visto por outras pessoas.
 *
 * @param link endereço completo, com {@code https://}, ou {@code null}
 * @param fotoUrl caminho da foto, ou {@code null} para mostrar as iniciais
 * @param capaUrl caminho da capa, ou {@code null} para o gradiente padrão
 * @param proprio se quem está vendo é o dono do perfil
 */
public record Perfil(
        UUID id,
        String username,
        String nomeCompleto,
        String bio,
        String link,
        List<String> habilidades,
        String fotoUrl,
        String capaUrl,
        Instant membroDesde,
        boolean proprio) {

    static Perfil de(Usuario usuario, boolean proprio) {
        return new Perfil(usuario.getId(), usuario.getUsername(), usuario.getNomeCompleto(), usuario.getBio(),
                usuario.getLink(), List.copyOf(usuario.getHabilidades()), usuario.getFotoPerfilUrl(),
                usuario.getCapaUrl(), usuario.getCriadoEm(), proprio);
    }
}
