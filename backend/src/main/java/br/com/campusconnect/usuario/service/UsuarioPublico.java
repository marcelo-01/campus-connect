package br.com.campusconnect.usuario.service;

import br.com.campusconnect.usuario.model.Usuario;
import java.util.UUID;

/**
 * O que qualquer pessoa logada pode ver de outra conta, como numa lista de membros. Não tem e-mail.
 *
 * @param fotoUrl caminho da foto de perfil, ou {@code null} para mostrar as iniciais
 */
public record UsuarioPublico(UUID id, String username, String nomeCompleto, String fotoUrl) {

    static UsuarioPublico de(Usuario usuario) {
        return new UsuarioPublico(usuario.getId(), usuario.getUsername(), usuario.getNomeCompleto(),
                usuario.getFotoPerfilUrl());
    }
}
