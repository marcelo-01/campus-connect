package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Unicidade de e-mail e username, usada no cadastro e na edição do perfil. */
@Component
@RequiredArgsConstructor
class RegrasConta {

    static final String EMAIL_EM_USO = "Este e-mail já está em uso.";
    static final String USERNAME_EM_USO = "Este nome de usuário já está em uso.";
    static final String USERNAME_RESERVADO = "Este nome de usuário não está disponível.";

    /**
     * Colidiriam com rotas fixas: {@code /perfil/editar} no frontend e {@code /api/usuarios/me} na API. O
     * "me" já é barrado pelo tamanho mínimo de 3 caracteres, mas fica aqui caso essa regra mude.
     */
    static final Set<String> USERNAMES_RESERVADOS = Set.of("editar", "me");

    private final UsuarioRepository usuarios;

    void garantirEmailLivre(String email) {
        if (usuarios.existsByEmail(email)) {
            throw new CampoDuplicadoException("email", EMAIL_EM_USO);
        }
    }

    /** @param dono conta que já usa esse username e pode mantê-lo; {@code null} no cadastro */
    void garantirUsernameLivre(String username, @Nullable UUID dono) {
        if (USERNAMES_RESERVADOS.contains(username)) {
            throw new CampoDuplicadoException("username", USERNAME_RESERVADO);
        }
        boolean emUso = dono == null
                ? usuarios.existsByUsername(username)
                : usuarios.existsByUsernameAndIdNot(username, dono);
        if (emUso) {
            throw new CampoDuplicadoException("username", USERNAME_EM_USO);
        }
    }

    /**
     * Duas requisições iguais ao mesmo tempo passam pelas checagens acima; o UNIQUE do banco desempata e
     * aqui o erro volta a apontar o campo certo.
     */
    static RuntimeException traduzirDuplicidade(DataIntegrityViolationException excecao) {
        String restricao = excecao.getCause() instanceof ConstraintViolationException violacao
                ? violacao.getConstraintName()
                : null;
        if ("usuario_email_key".equals(restricao)) {
            return new CampoDuplicadoException("email", EMAIL_EM_USO);
        }
        if ("usuario_username_key".equals(restricao)) {
            return new CampoDuplicadoException("username", USERNAME_EM_USO);
        }
        return excecao;
    }
}
