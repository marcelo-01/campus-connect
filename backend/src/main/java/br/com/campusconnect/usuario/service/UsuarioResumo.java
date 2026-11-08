package br.com.campusconnect.usuario.service;

import br.com.campusconnect.usuario.model.Usuario;
import java.util.UUID;

/** Dados públicos de uma conta, sem nada sensível. É o que os outros módulos recebem no lugar da entidade. */
public record UsuarioResumo(UUID id, String username, String nomeCompleto, String nomeExibicao, String email) {

    /** Fica restrito ao pacote para que a entidade não apareça na interface pública do módulo. */
    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(usuario.getId(), usuario.getUsername(), usuario.getNomeCompleto(),
                usuario.getNomeExibicao(), usuario.getEmail());
    }
}
