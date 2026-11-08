package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.service.AlteracaoPerfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corpo de {@code PUT /api/usuarios/me/perfil}.
 *
 * <p>Link e habilidades são normalizados e conferidos em {@code PerfilService}, que também é usado sem
 * passar por aqui.
 */
record AlteracaoPerfilRequest(
        @NotBlank(message = "Informe seu nome completo.")
        @Size(max = 150, message = "Use no máximo 150 caracteres.")
        String nomeCompleto,

        @NotBlank(message = "Escolha um nome de usuário.")
        @Pattern(regexp = "^[a-z0-9._]{3,30}$",
                message = "Use de 3 a 30 caracteres: letras, números, ponto ou sublinhado.")
        String username,

        @Size(max = 200, message = "A bio pode ter até 200 caracteres.")
        String bio,

        String link,

        List<String> habilidades) {

    AlteracaoPerfilRequest {
        nomeCompleto = nomeCompleto == null ? null : nomeCompleto.strip();
        username = username == null ? null : Usuario.normalizarUsername(username);
        bio = bio == null ? null : bio.strip();
    }

    AlteracaoPerfil paraAlteracao() {
        return new AlteracaoPerfil(nomeCompleto, username, bio, link, habilidades);
    }
}
