package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.arquivo.service.VideoService;
import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.publicacao.model.MidiaPublicacao;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import br.com.campusconnect.publicacao.model.TipoMidia;
import br.com.campusconnect.publicacao.repository.FeedRepository;
import br.com.campusconnect.publicacao.repository.PublicacaoRepository;
import br.com.campusconnect.publicacao.service.previa.PreviaLink;
import br.com.campusconnect.publicacao.service.previa.PreviaLinkService;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publicar, editar, excluir e ler publicações, e os feeds.
 *
 * <p>Quem vê um post é qualquer pessoa logada. Quem publica precisa ser membro da bolha. O que é
 * publicado numa bolha desce para as bolhas de dentro dela, mas não sobe: o feed de uma bolha mostra os
 * posts dela e os das bolhas acima.
 */
@Service
@RequiredArgsConstructor
public class PublicacaoService {

    static final int TAMANHO_PAGINA = 20;
    static final Duration JANELA_EM_ALTA = Duration.ofDays(7);

    private final PublicacaoRepository publicacoes;
    private final FeedRepository feeds;
    private final MontadorPublicacoes montador;
    private final BolhaService bolhas;
    private final UsuarioService usuarios;
    private final ImagemService imagens;
    private final VideoService videos;
    private final ArquivoService arquivos;
    private final PreviaLinkService previas;
    private final TransactionTemplate transacao;

    // ------------------------------------------------------------ escrita

    /**
     * Grava os arquivos e busca a prévia do link fora da transação, para não segurar uma conexão do banco
     * durante o upload e a leitura da página. Se algo der errado depois, os arquivos novos são apagados.
     *
     * @param imagens até 4 imagens, já conferidas em até 5 MB cada
     * @throws AcessoNegadoException se a pessoa não for membro da bolha
     */
    public PublicacaoVista publicar(UUID autor, NovaPublicacao nova, List<byte[]> imagens,
            @Nullable VideoEnviado video) {
        String texto = RegrasPublicacao.texto(nova.conteudo());
        String link = RegrasPublicacao.link(nova.link());
        RegrasPublicacao.garantirConteudo(texto, imagens.size(), video != null, link != null);
        BolhaResumo bolha = bolhas.resumos(List.of(nova.bolhaId()), autor).get(nova.bolhaId());
        if (bolha == null) {
            throw new CampoInvalidoException("bolhaId", "Escolha uma bolha que exista.");
        }
        RegrasPublicacao.garantirMembro(bolha.membro());

        List<String> gravados = new ArrayList<>();
        UUID id;
        try {
            List<String> imagensGravadas = new ArrayList<>();
            for (byte[] imagem : imagens) {
                String url = this.imagens.salvar(imagem, FormatoImagem.PUBLICACAO);
                gravados.add(url);
                imagensGravadas.add(url);
            }
            String videoGravado = video == null ? null : gravarVideo(video);
            if (videoGravado != null) {
                gravados.add(videoGravado);
            }
            PreviaLink previa = link == null ? null : previas.buscar(link).orElse(null);
            if (previa != null && previa.imagemUrl() != null) {
                gravados.add(previa.imagemUrl());
            }

            id = transacao.execute(status -> {
                Publicacao publicacao = new Publicacao(autor, bolha.id(), texto, StatusPublicacao.PUBLICADA);
                imagensGravadas.forEach(url -> publicacao.adicionarMidia(TipoMidia.IMAGEM, url).setMimeType("image/jpeg"));
                if (videoGravado != null) {
                    publicacao.adicionarMidia(TipoMidia.VIDEO, videoGravado)
                            .setMimeType(videoGravado.endsWith(".webm") ? "video/webm" : "video/mp4");
                }
                if (link != null) {
                    MidiaPublicacao midia = publicacao.adicionarMidia(TipoMidia.LINK, link);
                    if (previa != null) {
                        midia.setTitulo(previa.titulo());
                        midia.setDescricao(previa.descricao());
                        midia.setSite(previa.site());
                        midia.setImagemUrl(previa.imagemUrl());
                    }
                }
                // Se a transação desfizer, os arquivos novos vão embora junto.
                arquivos.descartarAoFim(List.of(), gravados);
                return publicacoes.saveAndFlush(publicacao).getId();
            });
        } catch (RuntimeException erro) {
            gravados.forEach(arquivos::remover);
            throw erro;
        }
        return buscar(autor, Objects.requireNonNull(id));
    }

    /** Só o texto muda; as mídias ficam como foram publicadas. */
    @Transactional
    public PublicacaoVista editar(UUID quem, UUID id, @Nullable String conteudo) {
        Publicacao publicacao = carregarPublicada(id);
        if (!publicacao.getAutorId().equals(quem)) {
            throw new AcessoNegadoException(RegrasPublicacao.SO_QUEM_ESCREVEU_EDITA);
        }
        String texto = RegrasPublicacao.texto(conteudo);
        if (texto.isEmpty() && publicacao.getMidias().isEmpty()) {
            throw new CampoInvalidoException("conteudo", "Escreva algo ou anexe uma mídia.");
        }
        if (!texto.equals(publicacao.getConteudo())) {
            publicacao.setConteudo(texto);
            publicacoes.flush();
        }
        return montador.uma(publicacao, quem).orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }

    /**
     * Some de todos os feeds na hora. O registro fica, marcado como removido, mas os arquivos são apagados
     * depois do commit.
     */
    @Transactional
    public void excluir(UUID quem, UUID id) {
        Publicacao publicacao = carregarPublicada(id);
        if (!publicacao.getAutorId().equals(quem) && !usuarios.ehAdministrador(quem)) {
            throw new AcessoNegadoException(RegrasPublicacao.SO_QUEM_ESCREVEU_EXCLUI);
        }
        publicacao.setStatus(StatusPublicacao.REMOVIDA);
        publicacao.setRemovidoEm(Instant.now());
        arquivos.descartarAoFim(arquivosDe(publicacao), List.of());
    }

    // ------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public PublicacaoVista buscar(UUID quemVe, UUID id) {
        return montador.uma(carregarPublicada(id), quemVe).orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }

    /**
     * O feed do Início: os posts das bolhas em que a pessoa está. Como entrar numa bolha também entra nas
     * de cima, os posts da bolha geral chegam por aqui.
     *
     * @param bolhaId quando vem, só os posts feitos nessa bolha, sem os que descem das de cima
     * @param ordem {@code recentes}, {@code alta} ou {@code midia}
     */
    @Transactional(readOnly = true)
    public PaginaFeed feedInicio(UUID quemVe, @Nullable UUID bolhaId, @Nullable String ordem, @Nullable String cursor) {
        Collection<UUID> origem = bolhaId != null ? List.of(bolhaId) : bolhas.idsDasBolhasDo(quemVe);
        return paginaDasBolhas(origem, OrdemFeed.de(ordem), Cursor.ler(cursor), quemVe);
    }

    /**
     * O feed de uma bolha: os posts dela e os das bolhas acima, nunca os das de dentro.
     *
     * @throws br.com.campusconnect.bolha.service.BolhaNaoEncontradaException se a bolha não existir
     */
    @Transactional(readOnly = true)
    public PaginaFeed feedBolha(UUID quemVe, UUID bolhaId, @Nullable String ordem, @Nullable String cursor) {
        return paginaDasBolhas(bolhas.idsDoCaminho(bolhaId), OrdemFeed.de(ordem), Cursor.ler(cursor), quemVe);
    }

    /** O feed de um perfil: os posts da pessoa e os que ela republicou. */
    @Transactional(readOnly = true)
    public PaginaFeed feedUsuario(UUID quemVe, UUID usuarioId, @Nullable String cursor) {
        Cursor inicio = Cursor.ler(cursor);
        return paginaPorData(feeds.doUsuario(usuarioId, inicio.em(), inicio.id(), TAMANHO_PAGINA + 1), quemVe);
    }

    /** Os posts que a pessoa salvou. Só ela vê. */
    @Transactional(readOnly = true)
    public PaginaFeed salvas(UUID quem, @Nullable String cursor) {
        Cursor inicio = Cursor.ler(cursor);
        return paginaPorData(feeds.salvasPor(quem, inicio.em(), inicio.id(), TAMANHO_PAGINA + 1), quem);
    }

    // ------------------------------------------------------------ apoio

    private PaginaFeed paginaDasBolhas(Collection<UUID> origem, OrdemFeed ordem, Cursor inicio, UUID quemVe) {
        if (ordem == OrdemFeed.ALTA) {
            List<FeedRepository.Item> itens = feeds.dasBolhasEmAlta(origem, Instant.now().minus(JANELA_EM_ALTA),
                    inicio.deslocamento(), TAMANHO_PAGINA + 1);
            boolean temMais = itens.size() > TAMANHO_PAGINA;
            List<FeedRepository.Item> pagina = temMais ? itens.subList(0, TAMANHO_PAGINA) : itens;
            return new PaginaFeed(montador.montar(pagina, quemVe),
                    temMais ? Cursor.deslocado(inicio.deslocamento() + TAMANHO_PAGINA) : null);
        }
        return paginaPorData(feeds.dasBolhas(origem, ordem == OrdemFeed.MIDIA, inicio.em(), inicio.id(),
                TAMANHO_PAGINA + 1), quemVe);
    }

    /** Recebe um item a mais que a página: se ele vier, há página seguinte, que começa depois do último. */
    private PaginaFeed paginaPorData(List<FeedRepository.Item> itens, UUID quemVe) {
        if (itens.size() <= TAMANHO_PAGINA) {
            return new PaginaFeed(montador.montar(itens, quemVe), null);
        }
        List<FeedRepository.Item> pagina = itens.subList(0, TAMANHO_PAGINA);
        FeedRepository.Item ultimo = pagina.getLast();
        return new PaginaFeed(montador.montar(pagina, quemVe), Cursor.depoisDe(ultimo.em(), ultimo.publicacaoId()));
    }

    private Publicacao carregarPublicada(UUID id) {
        return publicacoes.findByIdAndStatus(id, StatusPublicacao.PUBLICADA)
                .orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }

    private String gravarVideo(VideoEnviado video) {
        try (InputStream conteudo = video.conteudo().getInputStream()) {
            return videos.salvar(conteudo, video.tamanho());
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível ler o vídeo enviado", erro);
        }
    }

    static List<String> arquivosDe(Publicacao publicacao) {
        List<String> urls = new ArrayList<>();
        for (MidiaPublicacao midia : publicacao.getMidias()) {
            if (midia.getTipo() != TipoMidia.LINK) {
                urls.add(midia.getUrl());
            }
            if (midia.getImagemUrl() != null) {
                urls.add(midia.getImagemUrl());
            }
        }
        return urls;
    }
}
