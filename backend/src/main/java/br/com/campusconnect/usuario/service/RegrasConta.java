package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    /**
     * Uma conta que nunca verificou o e-mail segura o username por esse tempo. Depois disso, quem pedir o mesmo
     * username fica com ele, e a conta abandonada é apagada.
     */
    static final Duration RESERVA_USERNAME_NAO_VERIFICADO = Duration.ofHours(24);

    private final UsuarioRepository usuarios;

    /**
     * No cadastro: a conta que já tem o e-mail e ainda pode ser refeita por ele, porque nunca verificou o e-mail e
     * não foi suspensa, banida nem desativada.
     *
     * @throws CampoDuplicadoException se o e-mail for de uma conta verificada ou fora do ar
     */
    Optional<Usuario> contaParaRefazer(String email) {
        Optional<Usuario> existente = usuarios.findByEmail(email);
        if (existente.isPresent() && (existente.get().isEmailVerificado() || !existente.get().estaAtiva())) {
            throw new CampoDuplicadoException("email", EMAIL_EM_USO);
        }
        return existente;
    }

    /**
     * Libera, se for o caso, o username de uma conta não verificada há mais de
     * {@link #RESERVA_USERNAME_NAO_VERIFICADO}.
     *
     * @param dono conta que já usa esse username e pode mantê-lo; {@code null} num cadastro novo
     */
    void garantirUsernameLivre(String username, @Nullable UUID dono) {
        if (USERNAMES_RESERVADOS.contains(username)) {
            throw new CampoDuplicadoException("username", USERNAME_RESERVADO);
        }
        usuarios.apagarNaoVerificadaComUsername(username, Instant.now().minus(RESERVA_USERNAME_NAO_VERIFICADO), dono);
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
