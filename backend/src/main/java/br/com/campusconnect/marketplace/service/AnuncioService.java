package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.marketplace.model.Anuncio;
import br.com.campusconnect.marketplace.model.CategoriaAnuncio;
import br.com.campusconnect.marketplace.model.ModalidadePreco;
import br.com.campusconnect.marketplace.model.StatusAnuncio;
import br.com.campusconnect.marketplace.model.TipoAnuncio;
import br.com.campusconnect.marketplace.repository.AnuncioBolhaRepository;
import br.com.campusconnect.marketplace.repository.AnuncioRepository;
import br.com.campusconnect.marketplace.repository.CategoriaRepository;
import br.com.campusconnect.marketplace.repository.VitrineRepository;
import br.com.campusconnect.marketplace.repository.VitrineRepository.Filtro;
import br.com.campusconnect.marketplace.repository.VitrineRepository.Ordem;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anunciar, editar, pausar, excluir e ler anúncios, e a vitrine do marketplace.
 *
 * <p>Qualquer pessoa logada vê um anúncio publicado. Quem anuncia escolhe de 1 a 5 bolhas de que é
 * membro. Como entrar numa bolha também entra nas de cima, um anúncio na bolha geral chega a todo mundo
 * dela, e um anúncio num curso chega só a quem é do curso: desce pela hierarquia e não sobe, como os
 * posts.
 */
@Service
@RequiredArgsConstructor
public class AnuncioService {

    static final int TAMANHO_PAGINA = 20;

    private final AnuncioRepository anuncios;
    private final CategoriaRepository categorias;
    private final VitrineRepository vitrine;
    private final AnuncioBolhaRepository destinos;
    private final MontadorAnuncios montador;
    private final BolhaService bolhas;
    private final UsuarioService usuarios;
    private final ImagemService imagens;
    private final ArquivoService arquivos;
    private final TransactionTemplate transacao;
    private final ApplicationEventPublisher eventos;

    // ------------------------------------------------------------ escrita

    /**
     * Confere tudo antes de gravar as fotos, e grava as fotos fora da transação, para não segurar uma
     * conexão do banco durante o upload. Se algo der errado depois, as fotos novas são apagadas.
     *
     * @param fotos até 6, já conferidas em até 5 MB cada
     * @throws AcessoNegadoException se a pessoa não for membro de alguma das bolhas
     */
    public AnuncioDetalhe publicar(UUID anunciante, DadosAnuncio dados, List<byte[]> fotos) {
        Validado validado = validar(anunciante, dados);
        RegrasAnuncio.garantirFotos(fotos.size());

        List<String> gravadas = new ArrayList<>();
        UUID id;
        try {
            for (byte[] foto : fotos) {
                gravadas.add(imagens.salvar(foto, FormatoImagem.ANUNCIO));
            }
            id = transacao.execute(status -> {
                Anuncio anuncio = new Anuncio(anunciante, categorias.getReferenceById(validado.categoria().getId()),
                        validado.tipo(), validado.titulo(), StatusAnuncio.PUBLICADO);
                preencher(anuncio, validado);
                anuncio.setPublicadoEm(Instant.now());
                gravadas.forEach(anuncio::adicionarImagem);
                // Se a transação desfizer, as fotos novas vão embora junto.
                arquivos.descartarAoFim(List.of(), gravadas);
                UUID novo = anuncios.saveAndFlush(anuncio).getId();
                destinos.vincular(novo, validado.bolhas());
                return novo;
            });
        } catch (RuntimeException erro) {
            gravadas.forEach(arquivos::remover);
            throw erro;
        }
        return buscar(anunciante, Objects.requireNonNull(id));
    }

    /**
     * Troca os dados, as bolhas e as fotos. As fotos que saíram são apagadas do disco depois do commit.
     *
     * @param mantidas as fotos que o anúncio já tem e que ficam, na ordem; as novas entram depois delas
     */
    public AnuncioDetalhe editar(UUID quem, UUID id, DadosAnuncio dados, @Nullable List<String> mantidas,
            List<byte[]> fotosNovas) {
        Anuncio atual = carregarComImagens(id);
        garantirDono(atual, quem);
        Validado validado = validar(quem, dados);
        List<String> ficam = RegrasAnuncio.mantidas(mantidas, MontadorAnuncios.urls(atual));
        RegrasAnuncio.garantirFotos(ficam.size() + fotosNovas.size());

        List<String> gravadas = new ArrayList<>();
        try {
            for (byte[] foto : fotosNovas) {
                gravadas.add(imagens.salvar(foto, FormatoImagem.ANUNCIO));
            }
            transacao.executeWithoutResult(status -> {
                Anuncio anuncio = carregarAtivo(id);
                garantirDono(anuncio, quem);
                anuncio.setCategoria(categorias.getReferenceById(validado.categoria().getId()));
                anuncio.setTipo(validado.tipo());
                anuncio.setTitulo(validado.titulo());
                preencher(anuncio, validado);
                List<String> removidas = anuncio.trocarImagens(ficam, gravadas);
                arquivos.descartarAoFim(removidas, gravadas);
                anuncios.flush();
                destinos.vincular(id, validado.bolhas());
            });
        } catch (RuntimeException erro) {
            gravadas.forEach(arquivos::remover);
            throw erro;
        }
        return buscar(quem, id);
    }

    /** O anúncio some da vitrine, mas continua no perfil de quem anunciou, com a marca de pausado. */
    @Transactional
    public void pausar(UUID quem, UUID id) {
        Anuncio anuncio = carregarAtivo(id);
        garantirDono(anuncio, quem);
        anuncio.setStatus(StatusAnuncio.PAUSADO);
    }

    /** Volta para a vitrine no lugar em que estava: a data de publicação não muda. */
    @Transactional
    public void reativar(UUID quem, UUID id) {
        Anuncio anuncio = carregarAtivo(id);
        garantirDono(anuncio, quem);
        anuncio.setStatus(StatusAnuncio.PUBLICADO);
    }

    /**
     * Some de todo lugar na hora. O registro fica, marcado como removido, mas as fotos são apagadas depois
     * do commit.
     */
    @Transactional
    public void excluir(UUID quem, UUID id) {
        Anuncio anuncio = carregarAtivo(id);
        if (!anuncio.getAnuncianteId().equals(quem) && !usuarios.ehAdministrador(quem)) {
            throw new AcessoNegadoException(RegrasAnuncio.SO_QUEM_ANUNCIOU_EXCLUI);
        }
        anuncio.setStatus(StatusAnuncio.REMOVIDO);
        arquivos.descartarAoFim(MontadorAnuncios.urls(anuncio), List.of());
        eventos.publishEvent(new EventosMarketplace.AnuncioExcluido(id));
    }

    // ------------------------------------------------------------ leitura

    /**
     * O título de cada anúncio que ainda existe, publicado ou pausado, para quem mostra um anúncio de passagem, como
     * as notificações de quem anunciou. Os excluídos ficam de fora.
     */
    @Transactional(readOnly = true)
    public List<ResumoAnuncio> resumos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return anuncios.findByIdInAndStatusIn(ids, List.of(StatusAnuncio.PUBLICADO, StatusAnuncio.PAUSADO)).stream()
                .map(anuncio -> new ResumoAnuncio(anuncio.getId(), anuncio.getTitulo()))
                .toList();
    }

    /**
     * Um anúncio publicado, para qualquer pessoa logada. O pausado só aparece para quem anunciou.
     *
     * @throws AnuncioNaoEncontradoException se não existir, tiver sido excluído ou estiver pausado e quem
     *     pede não for quem anunciou
     */
    @Transactional(readOnly = true)
    public AnuncioDetalhe buscar(UUID quemVe, UUID id) {
        Anuncio anuncio = carregarVisivel(id, quemVe);
        return montador.detalhe(anuncio, quemVe).orElseThrow(() -> new AnuncioNaoEncontradoException(id));
    }

    /**
     * A vitrine do marketplace. Em "Minhas bolhas", os anúncios direcionados a alguma bolha em que a
     * pessoa está; com {@code bolhaId}, só os direcionados a essa bolha, em qualquer escopo.
     */
    @Transactional(readOnly = true)
    public PaginaAnuncios vitrine(UUID quemVe, FiltroVitrine filtro, @Nullable String cursor) {
        int deslocamento = CursorVitrine.ler(cursor);
        String escopo = filtro.escopo() == null || filtro.escopo().isBlank() ? "bolhas"
                : filtro.escopo().strip().toLowerCase(Locale.ROOT);
        Collection<UUID> bolhasDoFiltro = null;
        UUID favoritosDe = null;
        switch (escopo) {
            case "bolhas" -> bolhasDoFiltro = filtro.bolhaId() != null ? List.of(filtro.bolhaId())
                    : bolhas.idsDasBolhasDo(quemVe);
            case "rede" -> bolhasDoFiltro = filtro.bolhaId() != null ? List.of(filtro.bolhaId()) : null;
            case "favoritos" -> {
                favoritosDe = quemVe;
                bolhasDoFiltro = filtro.bolhaId() != null ? List.of(filtro.bolhaId()) : null;
            }
            default -> throw new CampoInvalidoException("escopo", "Escolha minhas bolhas, toda a rede ou favoritos.");
        }
        TipoAnuncio tipo = RegrasAnuncio.filtroDeTipo(filtro.tipo());
        String categoria = filtro.categoria() == null || filtro.categoria().isBlank() ? null
                : filtro.categoria().strip();
        Filtro consulta = new Filtro(bolhasDoFiltro, favoritosDe, tipo == null ? null : tipo.name(), categoria,
                RegrasAnuncio.busca(filtro.busca()));

        List<UUID> ids = vitrine.buscar(consulta, ordem(filtro.ordem()), deslocamento, TAMANHO_PAGINA + 1);
        Long total = deslocamento == 0 ? vitrine.contar(consulta) : null;
        return pagina(ids, deslocamento, quemVe, total);
    }

    /** Os anúncios de uma pessoa, para a aba Marketplace do perfil. Ela vê também os pausados. */
    @Transactional(readOnly = true)
    public PaginaAnuncios doUsuario(UUID quemVe, UUID usuarioId, @Nullable String cursor) {
        int deslocamento = CursorVitrine.ler(cursor);
        List<UUID> ids = vitrine.doAnunciante(usuarioId, usuarioId.equals(quemVe), deslocamento, TAMANHO_PAGINA + 1);
        return pagina(ids, deslocamento, quemVe, null);
    }

    @Transactional(readOnly = true)
    public List<CategoriaVista> categorias() {
        return categorias.ativasEmOrdem().stream().map(MontadorAnuncios::categoria).toList();
    }

    // ------------------------------------------------------------ apoio

    /** O que já foi conferido, pronto para gravar. */
    private record Validado(TipoAnuncio tipo, CategoriaAnuncio categoria, String titulo, String descricao,
            ModalidadePreco modalidade, @Nullable BigDecimal preco, Set<UUID> bolhas) {
    }

    private Validado validar(UUID quem, DadosAnuncio dados) {
        TipoAnuncio tipo = RegrasAnuncio.tipo(dados.tipo());
        String titulo = RegrasAnuncio.titulo(dados.titulo());
        String descricao = RegrasAnuncio.descricao(dados.descricao());
        ModalidadePreco modalidade = RegrasAnuncio.modalidade(dados.modalidadePreco());
        BigDecimal preco = RegrasAnuncio.preco(modalidade, dados.preco());

        CategoriaAnuncio categoria = dados.categoriaId() == null ? null
                : categorias.findById(dados.categoriaId()).filter(CategoriaAnuncio::isAtiva).orElse(null);
        if (categoria == null) {
            throw new CampoInvalidoException("categoriaId", "Escolha uma categoria.");
        }
        if (categoria.getTipo() != tipo) {
            throw new CampoInvalidoException("categoriaId", tipo == TipoAnuncio.SERVICO
                    ? "Escolha uma categoria de serviço." : "Escolha uma categoria de produto.");
        }

        Set<UUID> destino = RegrasAnuncio.bolhas(dados.bolhaIds());
        Map<UUID, BolhaResumo> resumos = bolhas.resumos(destino, quem);
        if (resumos.size() != destino.size()) {
            throw new CampoInvalidoException("bolhaIds", "Escolha bolhas que existam.");
        }
        resumos.values().forEach(bolha -> RegrasAnuncio.garantirMembro(bolha.membro()));
        return new Validado(tipo, categoria, titulo, descricao, modalidade, preco, destino);
    }

    private static void preencher(Anuncio anuncio, Validado validado) {
        anuncio.setDescricao(validado.descricao());
        anuncio.setModalidadePreco(validado.modalidade());
        anuncio.setPreco(validado.preco());
    }

    private PaginaAnuncios pagina(List<UUID> ids, int deslocamento, UUID quemVe, @Nullable Long total) {
        boolean temMais = ids.size() > TAMANHO_PAGINA;
        List<UUID> pagina = temMais ? ids.subList(0, TAMANHO_PAGINA) : ids;
        return new PaginaAnuncios(montador.cards(pagina, quemVe),
                temMais ? CursorVitrine.depoisDe(deslocamento + TAMANHO_PAGINA) : null, total);
    }

    private static Ordem ordem(@Nullable String ordem) {
        if (ordem == null || ordem.isBlank()) {
            return Ordem.RECENTES;
        }
        return switch (ordem.strip().toLowerCase(Locale.ROOT)) {
            case "recentes" -> Ordem.RECENTES;
            case "preco-asc" -> Ordem.PRECO_ASC;
            case "preco-desc" -> Ordem.PRECO_DESC;
            case "avaliacao" -> Ordem.AVALIACAO;
            default -> throw new CampoInvalidoException("ordem",
                    "Ordene por recentes, preco-asc, preco-desc ou avaliacao.");
        };
    }

    /** Publicado ou pausado: o que ainda pode ser alterado. */
    private Anuncio carregarAtivo(UUID id) {
        return anuncios.findById(id).filter(AnuncioService::ativo)
                .orElseThrow(() -> new AnuncioNaoEncontradoException(id));
    }

    private Anuncio carregarComImagens(UUID id) {
        return anuncios.carregarComImagens(List.of(id)).stream().filter(AnuncioService::ativo).findFirst()
                .orElseThrow(() -> new AnuncioNaoEncontradoException(id));
    }

    private Anuncio carregarVisivel(UUID id, UUID quemVe) {
        Anuncio anuncio = carregarComImagens(id);
        if (anuncio.getStatus() == StatusAnuncio.PAUSADO && !anuncio.getAnuncianteId().equals(quemVe)) {
            throw new AnuncioNaoEncontradoException(id);
        }
        return anuncio;
    }

    private static boolean ativo(Anuncio anuncio) {
        return anuncio.getStatus() == StatusAnuncio.PUBLICADO || anuncio.getStatus() == StatusAnuncio.PAUSADO;
    }

    private static void garantirDono(Anuncio anuncio, UUID quem) {
        if (!anuncio.getAnuncianteId().equals(quem)) {
            throw new AcessoNegadoException(RegrasAnuncio.SO_QUEM_ANUNCIOU_ALTERA);
        }
    }
}
