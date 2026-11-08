package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.service.NovoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/cadastro}.
 *
 * <p>O construtor normaliza e-mail e username antes da validação, então "@Ana.BC" vira "ana.bc" e passa no
 * padrão. Os limites batem com as colunas de {@code usuario}.
 */
record CadastroRequest(
        @NotBlank(message = "Informe seu nome completo.")
        @Size(max = 150, message = "Use no máximo 150 caracteres.")
        String nomeCompleto,

        @NotBlank(message = "Escolha um nome de usuário.")
        @Pattern(regexp = "^[a-z0-9._]{3,30}$",
                message = "Use de 3 a 30 caracteres: letras, números, ponto ou sublinhado.")
        String username,

        @NotBlank(message = "Informe seu e-mail.")
        @Email(regexp = ".+@.+\\..+", message = "Informe um e-mail válido.")
        @Size(max = 150, message = "Use no máximo 150 caracteres.")
        String email,

        @NotBlank(message = "Crie uma senha.")
        @Size(min = 8, max = 128, message = "A senha precisa ter de 8 a 128 caracteres.")
        String senha) {

    CadastroRequest {
        nomeCompleto = nomeCompleto == null ? null : nomeCompleto.strip();
        username = username == null ? null : Usuario.normalizarUsername(username);
        email = email == null ? null : Usuario.normalizarEmail(email);
    }

    NovoUsuario paraNovoUsuario() {
        return new NovoUsuario(nomeCompleto, username, email, senha);
    }

    @Override
    public String toString() {
        return "CadastroRequest[username=" + username + ", email=" + email + "]";
    }
}
