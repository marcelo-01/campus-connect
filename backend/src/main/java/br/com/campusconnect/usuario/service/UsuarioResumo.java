package br.com.campusconnect.usuario.service;

import br.com.campusconnect.usuario.model.Usuario;
import java.util.UUID;

/**
 * A própria conta de quem está logado. Tem o e-mail, então não serve para mostrar outras pessoas: para
 * isso existe {@link UsuarioPublico}.
 *
 * @param fotoUrl caminho da foto de perfil, ou {@code null} para mostrar as iniciais
 * @param administrador se pode criar, editar e excluir bolhas; o frontend usa para mostrar esses botões
 */
public record UsuarioResumo(UUID id, String username, String nomeCompleto, String nomeExibicao, String email,
        String fotoUrl, boolean administrador) {

    /** Fica restrito ao pacote para que a entidade não apareça na interface pública do módulo. */
    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(usuario.getId(), usuario.getUsername(), usuario.getNomeCompleto(),
                usuario.getNomeExibicao(), usuario.getEmail(), usuario.getFotoPerfilUrl(),
                usuario.podeAdministrar());
    }
}
