package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.marketplace.model.Anuncio;
import br.com.campusconnect.marketplace.model.AvaliacaoAnuncio;
import br.com.campusconnect.marketplace.model.CategoriaAnuncio;
import br.com.campusconnect.marketplace.model.ImagemAnuncio;
import br.com.campusconnect.marketplace.model.StatusAnuncio;
import br.com.campusconnect.marketplace.repository.AnuncioBolhaRepository;
import br.com.campusconnect.marketplace.repository.AnuncioRepository;
import br.com.campusconnect.marketplace.repository.AvaliacaoRepository;
import br.com.campusconnect.marketplace.repository.InteracaoAnuncioRepository;
import br.com.campusconnect.marketplace.repository.InteracaoAnuncioRepository.Nota;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Transforma anúncios em {@link AnuncioCard} e {@link AnuncioDetalhe}. Lê tudo em lote: os anúncios com a
 * categoria e as fotos, quem anunciou, as bolhas, as notas e os favoritos de quem vê.
 *
 * <p>Anúncios de contas suspensas ou desativadas ficam de fora, como em todo o resto do sistema. Um
 * anúncio sem bolha, porque as bolhas dele foram excluídas, continua: só a lista de bolhas vem vazia.
 */
@Component
@RequiredArgsConstructor
class MontadorAnuncios {

    /** As bolhas de um anúncio vêm das mais amplas para as mais específicas. */
    private static final Comparator<BolhaResumo> ORDEM_DAS_BOLHAS = Comparator.comparingInt(BolhaResumo::nivel)
            .thenComparing(BolhaResumo::nome, String.CASE_INSENSITIVE_ORDER);

    private final AnuncioRepository anuncios;
    private final AnuncioBolhaRepository destinos;
    private final InteracaoAnuncioRepository interacoes;
    private final AvaliacaoRepository avaliacoes;
    private final UsuarioService usuarios;
    private final BolhaService bolhas;

    /** Os cards na ordem dos ids. */
    List<AnuncioCard> cards(List<UUID> ids, UUID quemVe) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Anuncio> porId = anuncios.carregarComImagens(ids).stream()
                .collect(Collectors.toMap(Anuncio::getId, Function.identity()));
        Contexto contexto = contexto(porId.values(), quemVe, Set.of());
        List<AnuncioCard> cards = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Anuncio anuncio = porId.get(id);
            AnuncioCard card = anuncio == null ? null : card(anuncio, contexto);
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    /**
     * A página de um anúncio já carregado com a categoria e as fotos; vazio se quem anunciou estiver
     * suspenso.
     */
    Optional<AnuncioDetalhe> detalhe(Anuncio anuncio, UUID quemVe) {
        AvaliacaoAnuncio minha = avaliacoes.findByAnuncio_IdAndAutorId(anuncio.getId(), quemVe).orElse(null);
        Contexto contexto = contexto(List.of(anuncio), quemVe, minha == null ? Set.of() : Set.of(quemVe));
        AnuncioCard card = card(anuncio, contexto);
        if (card == null) {
            return Optional.empty();
        }
        boolean dono = anuncio.getAnuncianteId().equals(quemVe);
        boolean administrador = usuarios.ehAdministrador(quemVe);
        UsuarioPublico eu = contexto.pessoas().get(quemVe);
        AvaliacaoVista minhaAvaliacao = minha == null || eu == null ? null : avaliacao(minha, eu, true);
        return Optional.of(new AnuncioDetalhe(card.id(), card.tipo(), card.titulo(), card.categoria(),
                card.modalidadePreco(), card.preco(), card.capaUrl(), card.anunciante(), card.bolhas(), card.media(),
                card.totalAvaliacoes(), card.favorito(), card.status(), anuncio.getDescricao(), urls(anuncio),
                anuncio.getPublicadoEm(), minhaAvaliacao, dono, dono || administrador,
                !dono && anuncio.getStatus() == StatusAnuncio.PUBLICADO));
    }

    /** Avaliações de um anúncio, sem as de contas suspensas, na ordem dada. */
    List<AvaliacaoVista> avaliacoes(List<AvaliacaoAnuncio> lista, UUID quemVe) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<UUID, UsuarioPublico> autores = publicos(lista.stream().map(AvaliacaoAnuncio::getAutorId).toList());
        boolean administrador = usuarios.ehAdministrador(quemVe);
        List<AvaliacaoVista> vistas = new ArrayList<>(lista.size());
        for (AvaliacaoAnuncio avaliacao : lista) {
            UsuarioPublico autor = autores.get(avaliacao.getAutorId());
            if (autor != null) {
                vistas.add(avaliacao(avaliacao, autor, administrador || avaliacao.getAutorId().equals(quemVe)));
            }
        }
        return vistas;
    }

    static CategoriaVista categoria(CategoriaAnuncio categoria) {
        return new CategoriaVista(categoria.getId(), categoria.getNome(), categoria.getSlug(),
                categoria.getTipo().name());
    }

    static List<String> urls(Anuncio anuncio) {
        return anuncio.getImagens().stream().map(ImagemAnuncio::getUrl).toList();
    }

    // ------------------------------------------------------------ apoio

    private Contexto contexto(Collection<Anuncio> lista, UUID quemVe, Set<UUID> outrasPessoas) {
        Set<UUID> ids = lista.stream().map(Anuncio::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> pessoas = new LinkedHashSet<>();
        lista.forEach(anuncio -> pessoas.add(anuncio.getAnuncianteId()));
        pessoas.addAll(outrasPessoas);

        Map<UUID, List<UUID>> bolhasPorAnuncio = destinos.bolhasDe(ids);
        Set<UUID> todasAsBolhas = new HashSet<>();
        bolhasPorAnuncio.values().forEach(todasAsBolhas::addAll);
        Map<UUID, BolhaResumo> resumos = todasAsBolhas.isEmpty() ? Map.of() : bolhas.resumos(todasAsBolhas, quemVe);

        return new Contexto(publicos(List.copyOf(pessoas)), bolhasPorAnuncio, resumos, interacoes.notas(ids),
                interacoes.favoritosDentre(quemVe, ids));
    }

    private Map<UUID, UsuarioPublico> publicos(List<UUID> ids) {
        return usuarios.buscarPublicos(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UsuarioPublico::id, Function.identity()));
    }

    private static @Nullable AnuncioCard card(Anuncio anuncio, Contexto contexto) {
        UsuarioPublico anunciante = contexto.pessoas().get(anuncio.getAnuncianteId());
        if (anunciante == null) {
            return null;
        }
        UUID id = anuncio.getId();
        List<BolhaResumo> destino = contexto.bolhasPorAnuncio().getOrDefault(id, List.of()).stream()
                .map(contexto.resumos()::get).filter(Objects::nonNull).sorted(ORDEM_DAS_BOLHAS).toList();
        Nota nota = contexto.notas().get(id);
        List<ImagemAnuncio> imagens = anuncio.getImagens();
        return new AnuncioCard(id, anuncio.getTipo().name(), anuncio.getTitulo(), categoria(anuncio.getCategoria()),
                anuncio.getModalidadePreco() == null ? null : anuncio.getModalidadePreco().name(), anuncio.getPreco(),
                imagens.isEmpty() ? null : imagens.getFirst().getUrl(), anunciante, destino,
                nota == null ? null : nota.media(), nota == null ? 0 : nota.total(),
                contexto.favoritos().contains(id), anuncio.getStatus().name());
    }

    private static AvaliacaoVista avaliacao(AvaliacaoAnuncio avaliacao, UsuarioPublico autor, boolean podeExcluir) {
        return new AvaliacaoVista(avaliacao.getId(), autor, avaliacao.getNota(), avaliacao.getComentario(),
                avaliacao.getCriadoEm(), podeExcluir);
    }

    private record Contexto(Map<UUID, UsuarioPublico> pessoas, Map<UUID, List<UUID>> bolhasPorAnuncio,
            Map<UUID, BolhaResumo> resumos, Map<UUID, Nota> notas, Set<UUID> favoritos) {
    }
}
