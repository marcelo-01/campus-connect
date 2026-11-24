package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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

    /**
     * Cria a conta ativa, mas com o e-mail ainda por verificar. Se o e-mail for de uma conta que nunca foi verificada,
     * refaz essa conta com os dados novos (ver {@link Usuario#refazerCadastro}).
     *
     * <p>Só pacote: quem cadastra é o {@link AutenticacaoService}, que também manda o código.
     *
     * @throws CampoDuplicadoException se o e-mail ou o username estiverem em uso
     */
    @Transactional
    Usuario cadastrar(NovoUsuario novo) {
        String email = Usuario.normalizarEmail(novo.email());
        String username = Usuario.normalizarUsername(novo.username());
        String nome = novo.nomeCompleto().strip();

        // Confere antes de calcular o hash: o Argon2 é caro de propósito.
        Optional<Usuario> pendente = regras.contaParaRefazer(email);
        regras.garantirUsernameLivre(username, pendente.map(Usuario::getId).orElse(null));

        String senhaHash = senhas.encode(novo.senha());
        Usuario usuario = pendente.orElseGet(() -> new Usuario(username, nome, email, senhaHash, StatusUsuario.ATIVO));
        usuario.refazerCadastro(username, nome, senhaHash);
        try {
            return usuarios.saveAndFlush(usuario);
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

    /**
     * Entre os ids, os das contas que aparecem para outras pessoas (as mesmas de {@link #buscarPublicos}) e, se houver
     * termo, cujo nome ou username o contém, sem diferenciar maiúsculas. O {@code @} da frente é ignorado, e
     * {@code %} e {@code _} valem como texto.
     *
     * <p>Serve para filtrar uma lista antes de paginar, para nenhuma página vir curta.
     */
    @Transactional(readOnly = true)
    public Set<UUID> publicosEntre(Collection<UUID> ids, @Nullable String termo) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        String busca = termo == null ? "" : termo.strip().replaceFirst("^@", "");
        String padrao = busca.isEmpty() ? "%" : "%" + semCuringas(busca) + "%";
        return Set.copyOf(usuarios.idsPublicosQueCasam(Set.copyOf(ids), padrao));
    }

    private static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
