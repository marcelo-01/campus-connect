package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RegrasConta regras;
    private final PasswordEncoder senhas;

    /** Cria a conta já ativa. Falha com {@link CampoDuplicadoException} se o e-mail ou o username estiverem em uso. */
    @Transactional
    public UsuarioResumo cadastrar(NovoUsuario novo) {
        String email = Usuario.normalizarEmail(novo.email());
        String username = Usuario.normalizarUsername(novo.username());

        // Confere antes de calcular o hash: o Argon2 é caro de propósito.
        regras.garantirEmailLivre(email);
        regras.garantirUsernameLivre(username, null);

        Usuario usuario = new Usuario(username, novo.nomeCompleto().strip(), email, senhas.encode(novo.senha()),
                StatusUsuario.ATIVO);
        try {
            return UsuarioResumo.de(usuarios.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException corrida) {
            throw RegrasConta.traduzirDuplicidade(corrida);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioResumo> buscarResumo(UUID id) {
        return usuarios.findById(id).map(UsuarioResumo::de);
    }
}
