package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /** Lê do banco a cada chamada: tirar a permissão por SQL vale na hora, sem esperar o token vencer. */
    @Transactional(readOnly = true)
    public boolean ehAdministrador(UUID id) {
        return usuarios.findById(id).map(Usuario::podeAdministrar).orElse(false);
    }

    /**
     * Contas que podem aparecer para outras pessoas, na ordem dos ids pedidos. Contas suspensas, banidas,
     * desativadas ou anonimizadas ficam de fora, como no perfil.
     */
    @Transactional(readOnly = true)
    public List<UsuarioPublico> buscarPublicos(List<UUID> ids) {
        Map<UUID, Usuario> porId = usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return ids.stream()
                .map(porId::get)
                .filter(usuario -> usuario != null && usuario.podeEntrar())
                .map(UsuarioPublico::de)
                .toList();
    }
}
