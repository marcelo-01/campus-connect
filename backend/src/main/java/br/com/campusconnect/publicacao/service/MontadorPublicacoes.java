package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.publicacao.model.MidiaPublicacao;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.TipoMidia;
import br.com.campusconnect.publicacao.repository.FeedRepository;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import br.com.campusconnect.publicacao.repository.PublicacaoRepository;
import br.com.campusconnect.publicacao.service.PublicacaoVista.LinkVista;
import br.com.campusconnect.publicacao.service.PublicacaoVista.MidiaVista;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.ArrayList;
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
import org.springframework.stereotype.Component;

/**
 * Transforma os itens de uma página do feed em {@link ItemFeed}. Lê tudo em lote: as publicações com as
 * mídias, os autores, as bolhas, as contagens e o que quem vê já fez, em poucas consultas por página.
 *
 * <p>Posts de contas suspensas ou desativadas ficam de fora, como em todo o resto do sistema.
 */
@Component
@RequiredArgsConstructor
class MontadorPublicacoes {

    private final PublicacaoRepository publicacoes;
    private final InteracaoRepository interacoes;
    private final UsuarioService usuarios;
    private final BolhaService bolhas;

    List<ItemFeed> montar(List<FeedRepository.Item> itens, UUID quemVe) {
        if (itens.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = itens.stream().map(FeedRepository.Item::publicacaoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Publicacao> porId = publicacoes.carregarComMidias(ids).stream()
                .collect(Collectors.toMap(Publicacao::getId, Function.identity()));

        Set<UUID> pessoas = new LinkedHashSet<>();
        porId.values().forEach(publicacao -> pessoas.add(publicacao.getAutorId()));
        itens.stream().map(FeedRepository.Item::republicadaPor).filter(Objects::nonNull)
                .forEach(pessoas::add);
        Map<UUID, UsuarioPublico> publicos = usuarios.buscarPublicos(List.copyOf(pessoas)).stream()
                .collect(Collectors.toMap(UsuarioPublico::id, Function.identity()));
        Map<UUID, BolhaResumo> bolhasPorId = bolhas.resumos(
                porId.values().stream().map(Publicacao::getBolhaId).collect(Collectors.toSet()), quemVe);

        Contexto contexto = new Contexto(quemVe, usuarios.ehAdministrador(quemVe),
                interacoes.curtidasPorPublicacao(ids), interacoes.comentariosPorPublicacao(ids),
                interacoes.republicacoesPorPublicacao(ids), interacoes.curtidasPor(quemVe, ids),
                interacoes.salvasPor(quemVe, ids), interacoes.republicadasPor(quemVe, ids));

        List<ItemFeed> montados = new ArrayList<>(itens.size());
        for (FeedRepository.Item item : itens) {
            Publicacao publicacao = porId.get(item.publicacaoId());
            if (publicacao == null) {
                continue;
            }
            UsuarioPublico autor = publicos.get(publicacao.getAutorId());
            BolhaResumo bolha = bolhasPorId.get(publicacao.getBolhaId());
            UsuarioPublico republicadaPor = item.republicadaPor() == null ? null : publicos.get(item.republicadaPor());
            if (autor == null || bolha == null || (item.republicadaPor() != null && republicadaPor == null)) {
                continue;
            }
            montados.add(new ItemFeed(vista(publicacao, autor, bolha, contexto), republicadaPor, item.em()));
        }
        return montados;
    }

    /** Uma publicação sozinha; vazio se o autor estiver suspenso. */
    Optional<PublicacaoVista> uma(Publicacao publicacao, UUID quemVe) {
        return montar(List.of(new FeedRepository.Item(publicacao.getId(), null, publicacao.getCriadoEm())), quemVe)
                .stream().findFirst().map(ItemFeed::publicacao);
    }

    private static PublicacaoVista vista(Publicacao publicacao, UsuarioPublico autor, BolhaResumo bolha,
            Contexto contexto) {
        UUID id = publicacao.getId();
        List<MidiaVista> midias = new ArrayList<>();
        LinkVista link = null;
        for (MidiaPublicacao midia : publicacao.getMidias()) {
            if (midia.getTipo() == TipoMidia.LINK) {
                link = new LinkVista(midia.getUrl(), midia.getTitulo(), midia.getDescricao(), midia.getSite(),
                        midia.getImagemUrl());
            } else {
                midias.add(new MidiaVista(midia.getTipo().name(), midia.getUrl(), midia.getMimeType()));
            }
        }
        boolean proprio = publicacao.getAutorId().equals(contexto.quemVe());
        return new PublicacaoVista(id, autor, bolha, publicacao.getConteudo(), List.copyOf(midias), link,
                publicacao.getCriadoEm(), publicacao.getAtualizadoEm(),
                contexto.curtidas().getOrDefault(id, 0L), contexto.comentarios().getOrDefault(id, 0L),
                contexto.republicacoes().getOrDefault(id, 0L), contexto.curtiu(id), contexto.salvou(id),
                contexto.republicou(id), bolha.membro(), proprio, proprio || contexto.administrador());
    }

    private record Contexto(UUID quemVe, boolean administrador, Map<UUID, Long> curtidas,
            Map<UUID, Long> comentarios, Map<UUID, Long> republicacoes, Set<UUID> curtidasPorQuemVe,
            Set<UUID> salvasPorQuemVe, Set<UUID> republicadasPorQuemVe) {

        boolean curtiu(UUID id) {
            return curtidasPorQuemVe.contains(id);
        }

        boolean salvou(UUID id) {
            return salvasPorQuemVe.contains(id);
        }

        boolean republicou(UUID id) {
            return republicadasPorQuemVe.contains(id);
        }
    }
}
