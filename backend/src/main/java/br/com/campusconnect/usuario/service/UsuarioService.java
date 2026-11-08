package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    static final String EMAIL_EM_USO = "Este e-mail já está em uso.";
    static final String USERNAME_EM_USO = "Este nome de usuário já está em uso.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;

    /** Cria a conta já ativa. Falha com {@link CampoDuplicadoException} se o e-mail ou o username estiverem em uso. */
    @Transactional
    public UsuarioResumo cadastrar(NovoUsuario novo) {
        String email = Usuario.normalizarEmail(novo.email());
        String username = Usuario.normalizarUsername(novo.username());

        // Confere antes de calcular o hash: o Argon2 é caro de propósito.
        if (usuarios.existsByEmail(email)) {
            throw new CampoDuplicadoException("email", EMAIL_EM_USO);
        }
        if (usuarios.existsByUsername(username)) {
            throw new CampoDuplicadoException("username", USERNAME_EM_USO);
        }

        Usuario usuario = new Usuario(username, novo.nomeCompleto().strip(), email, senhas.encode(novo.senha()),
                StatusUsuario.ATIVO);
        try {
            return UsuarioResumo.de(usuarios.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException corrida) {
            // Dois cadastros iguais ao mesmo tempo passam pelas checagens acima; o UNIQUE do banco desempata.
            throw traduzirDuplicidade(corrida);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioResumo> buscarResumo(UUID id) {
        return usuarios.findById(id).map(UsuarioResumo::de);
    }

    private static RuntimeException traduzirDuplicidade(DataIntegrityViolationException excecao) {
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
