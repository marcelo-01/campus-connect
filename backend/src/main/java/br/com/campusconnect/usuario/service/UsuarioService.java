package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.BuscaPessoasRepository;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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

    static final int TAMANHO_BUSCA = 100;
    static final int TAMANHO_PAGINA_BUSCA = 20;
    /** Da 11ª palavra em diante, nada entra na busca de pessoas: nenhum nome tem tantas. */
    static final int MAXIMO_PALAVRAS_BUSCA = 10;

    private final UsuarioRepository usuarios;
    private final BuscaPessoasRepository busca;
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

    /**
     * A aba Pessoas da busca e as sugestões do campo de busca: as contas que aparecem para outras pessoas cujo nome ou
     * username tem as palavras buscadas, do que casa melhor para o que casa pior (ver {@link BuscaPessoasRepository}).
     * O {@code @} da frente de cada palavra é ignorado. Busca vazia não acha ninguém.
     *
     * @param cursor o {@code proximo} da página anterior, para continuar
     * @param limite quantas pessoas por página, de 1 a 20; sem ele, 20
     */
    @Transactional(readOnly = true)
    public PaginaPessoas buscarPessoas(@Nullable String termo, @Nullable String cursor, @Nullable Integer limite) {
        List<String> palavras = palavrasDaBusca(termo);
        int deslocamento = deslocamentoDaBusca(cursor);
        int tamanho = limite == null ? TAMANHO_PAGINA_BUSCA : Math.clamp(limite, 1, TAMANHO_PAGINA_BUSCA);
        if (palavras.isEmpty()) {
            return new PaginaPessoas(List.of(), null);
        }
        // Um a mais que a página: se ele vier, há página seguinte.
        List<UUID> ids = busca.buscar(palavras, deslocamento, tamanho + 1);
        boolean temMais = ids.size() > tamanho;
        return new PaginaPessoas(buscarPublicos(temMais ? ids.subList(0, tamanho) : ids),
                temMais ? String.valueOf(deslocamento + tamanho) : null);
    }

    private static List<String> palavrasDaBusca(@Nullable String termo) {
        String texto = termo == null ? "" : termo.strip();
        if (texto.length() > TAMANHO_BUSCA) {
            throw new CampoInvalidoException("q", "A busca pode ter até " + TAMANHO_BUSCA + " caracteres.");
        }
        return Arrays.stream(texto.split("\\s+"))
                .map(palavra -> palavra.replaceFirst("^@+", "").toLowerCase(Locale.ROOT))
                .filter(palavra -> !palavra.isEmpty())
                .distinct()
                .limit(MAXIMO_PALAVRAS_BUSCA)
                .toList();
    }

    private static int deslocamentoDaBusca(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int deslocamento = Integer.parseInt(cursor.strip());
            if (deslocamento >= 0) {
                return deslocamento;
            }
        } catch (NumberFormatException invalido) {
            // Responde como a qualquer outro cursor que não serve.
        }
        throw new CampoInvalidoException("cursor", "Não foi possível continuar a busca. Recarregue a página.");
    }

    private static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
