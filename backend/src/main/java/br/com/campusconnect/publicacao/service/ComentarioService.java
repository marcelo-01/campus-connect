package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.publicacao.model.Comentario;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import br.com.campusconnect.publicacao.repository.ComentarioRepository;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import br.com.campusconnect.publicacao.repository.PublicacaoRepository;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comentários de uma publicação, com um nível de respostas. Responder a uma resposta pendura a nova no
 * mesmo comentário de cima, como nas redes sociais mais comuns.
 *
 * <p>Comentar e curtir comentário exigem ser membro da bolha do post. Excluir pode quem escreveu ou um
 * administrador. O comentário excluído some; se tiver respostas, fica no lugar dele um "Comentário
 * removido", sem autor e sem texto, para as respostas não perderem o contexto.
 */
@Service
@RequiredArgsConstructor
public class ComentarioService {

    /** Segura a resposta até a lista ganhar paginação. */
    static final int LIMITE = 200;

    private static final String SO_QUEM_ESCREVEU = "Só quem escreveu ou um administrador pode excluir o comentário.";

    private final PublicacaoRepository publicacoes;
    private final ComentarioRepository comentarios;
    private final InteracaoRepository interacoes;
    private final BolhaService bolhas;
    private final UsuarioService usuarios;

    /** @param ordem {@code relevantes} (mais curtidos primeiro) ou {@code recentes} (mais novos primeiro) */
    @Transactional(readOnly = true)
    public List<ComentarioVista> listar(UUID quemVe, UUID publicacaoId, @Nullable String ordem) {
        boolean recentes = ordemRecentes(ordem);
        carregarPublicada(publicacaoId);
        List<Comentario> principais = comentarios.principais(publicacaoId);
        List<Comentario> respostas = principais.isEmpty()
                ? List.of()
                : comentarios.respostasA(principais.stream().map(Comentario::getId).toList());

        List<Comentario> todos = new ArrayList<>(principais);
        todos.addAll(respostas);
        Set<UUID> ids = todos.stream().map(Comentario::getId).collect(Collectors.toSet());
        Map<UUID, Long> curtidas = interacoes.curtidasPorComentario(ids);
        Set<UUID> curtidosPorQuemVe = interacoes.comentariosCurtidosPor(quemVe, ids);
        Map<UUID, UsuarioPublico> autores = usuarios.buscarPublicos(todos.stream()
                        .filter(c -> c.getRemovidoEm() == null).map(Comentario::getAutorId).distinct().toList())
                .stream().collect(Collectors.toMap(UsuarioPublico::id, Function.identity()));
        boolean administrador = usuarios.ehAdministrador(quemVe);

        Map<UUID, List<ComentarioVista>> respostasPorPai = new LinkedHashMap<>();
        for (Comentario resposta : respostas) {
            UsuarioPublico autor = autores.get(resposta.getAutorId());
            if (resposta.getRemovidoEm() == null && autor != null) {
                respostasPorPai.computeIfAbsent(resposta.getComentarioPai().getId(), chave -> new ArrayList<>())
                        .add(vista(resposta, autor, curtidas, curtidosPorQuemVe, quemVe, administrador, List.of()));
            }
        }

        List<ComentarioVista> lista = new ArrayList<>();
        for (Comentario principal : principais) {
            List<ComentarioVista> suas = respostasPorPai.getOrDefault(principal.getId(), List.of());
            UsuarioPublico autor = autores.get(principal.getAutorId());
            if (principal.getRemovidoEm() == null && autor != null) {
                lista.add(vista(principal, autor, curtidas, curtidosPorQuemVe, quemVe, administrador, suas));
            } else if (!suas.isEmpty()) {
                lista.add(new ComentarioVista(principal.getId(), null, null, principal.getCriadoEm(), 0, false,
                        true, false, suas));
            }
        }

        Comparator<ComentarioVista> porData = Comparator.comparing(ComentarioVista::criadoEm);
        lista.sort(recentes
                ? porData.reversed()
                : Comparator.comparingLong(ComentarioVista::curtidas).reversed().thenComparing(porData));
        return lista.size() > LIMITE ? List.copyOf(lista.subList(0, LIMITE)) : lista;
    }

    /**
     * @param respondendoA comentário a que este responde; se for uma resposta, a nova pendura no comentário
     *     de cima dela
     */
    @Transactional
    public ComentarioVista comentar(UUID quem, UUID publicacaoId, @Nullable String conteudo,
            @Nullable UUID respondendoA) {
        Publicacao publicacao = carregarPublicada(publicacaoId);
        RegrasPublicacao.garantirMembro(bolhas.ehMembro(quem, publicacao.getBolhaId()));
        String texto = RegrasPublicacao.comentario(conteudo);

        Comentario pai = null;
        if (respondendoA != null) {
            Comentario alvo = carregarAtivo(respondendoA);
            if (!alvo.getPublicacao().getId().equals(publicacaoId)) {
                throw new CampoInvalidoException("respondendoA", "Esse comentário é de outra publicação.");
            }
            pai = alvo.getComentarioPai() != null ? alvo.getComentarioPai() : alvo;
        }
        Comentario comentario = new Comentario(publicacao, quem, texto);
        comentario.setComentarioPai(pai);
        comentarios.saveAndFlush(comentario);

        UsuarioPublico autor = usuarios.buscarPublicos(List.of(quem)).stream().findFirst()
                .orElseThrow(() -> new AcessoNegadoException("Sua conta não pode comentar."));
        return vista(comentario, autor, Map.of(), Set.of(), quem, false, List.of());
    }

    @Transactional
    public void excluir(UUID quem, UUID comentarioId) {
        Comentario comentario = carregarAtivo(comentarioId);
        if (!comentario.getAutorId().equals(quem) && !usuarios.ehAdministrador(quem)) {
            throw new AcessoNegadoException(SO_QUEM_ESCREVEU);
        }
        comentario.setRemovidoEm(Instant.now());
    }

    @Transactional
    public void curtir(UUID quem, UUID comentarioId) {
        Comentario comentario = carregarAtivo(comentarioId);
        RegrasPublicacao.garantirMembro(bolhas.ehMembro(quem, comentario.getPublicacao().getBolhaId()));
        interacoes.curtirComentario(quem, comentarioId);
    }

    @Transactional
    public void descurtir(UUID quem, UUID comentarioId) {
        carregarAtivo(comentarioId);
        interacoes.descurtirComentario(quem, comentarioId);
    }

    // ------------------------------------------------------------ apoio

    private static boolean ordemRecentes(@Nullable String ordem) {
        if (ordem == null || ordem.isBlank()) {
            return false;
        }
        return switch (ordem.strip().toLowerCase(Locale.ROOT)) {
            case "relevantes" -> false;
            case "recentes" -> true;
            default -> throw new CampoInvalidoException("ordem", "Use relevantes ou recentes.");
        };
    }

    private static ComentarioVista vista(Comentario comentario, UsuarioPublico autor, Map<UUID, Long> curtidas,
            Set<UUID> curtidosPorQuemVe, UUID quemVe, boolean administrador, List<ComentarioVista> respostas) {
        boolean proprio = comentario.getAutorId().equals(quemVe);
        return new ComentarioVista(comentario.getId(), autor, comentario.getConteudo(), comentario.getCriadoEm(),
                curtidas.getOrDefault(comentario.getId(), 0L), curtidosPorQuemVe.contains(comentario.getId()),
                false, proprio || administrador, List.copyOf(respostas));
    }

    private Publicacao carregarPublicada(UUID id) {
        return publicacoes.findByIdAndStatus(id, StatusPublicacao.PUBLICADA)
                .orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }

    /** O comentário, se ele e a publicação dele ainda existirem. */
    private Comentario carregarAtivo(UUID id) {
        return comentarios.findById(id)
                .filter(c -> c.getRemovidoEm() == null)
                .filter(c -> c.getPublicacao().getStatus() == StatusPublicacao.PUBLICADA)
                .orElseThrow(() -> new ComentarioNaoEncontradoException(id));
    }
}
