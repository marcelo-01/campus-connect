package br.com.campusconnect.notificacao.service;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import br.com.campusconnect.marketplace.service.AnuncioService;
import br.com.campusconnect.marketplace.service.ResumoAnuncio;
import br.com.campusconnect.notificacao.model.TipoNotificacao;
import br.com.campusconnect.notificacao.repository.NotificacaoRepository;
import br.com.campusconnect.notificacao.repository.NotificacaoRepository.Grupo;
import br.com.campusconnect.notificacao.repository.NotificacaoRepository.Linha;
import br.com.campusconnect.publicacao.service.PublicacaoService;
import br.com.campusconnect.publicacao.service.ResumoPublicacao;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A tela de notificações e o contador do menu. Quem grava as notificações é o {@link Notificador}, a partir dos
 * eventos dos outros módulos.
 *
 * <p>Abrir a tela marca como lidas as notificações que ela mostrou, e as que eram novas continuam destacadas
 * naquela visita: a página já veio com elas marcadas como novas.
 *
 * <p>Uma notificação cujo alvo saiu do ar, ou de quem não aparece mais para os outros, fica fora da página. O alvo
 * que sai do ar costuma levar as notificações junto; isto cobre o que escapar.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    /** O evento ao vivo que avisa que o contador mudou. Vai sem dados: a tela pergunta o número de novo. */
    public static final String EVENTO = "notificacoes";

    static final int TAMANHO_PAGINA = 20;
    static final int PESSOAS_POR_GRUPO = 3;

    /** As que levam a uma publicação: a referência delas é o id da publicação. */
    private static final Set<TipoNotificacao> DE_PUBLICACAO = EnumSet.of(TipoNotificacao.NOVO_COMENTARIO,
            TipoNotificacao.RESPOSTA_COMENTARIO, TipoNotificacao.CURTIDA_PUBLICACAO,
            TipoNotificacao.CURTIDA_COMENTARIO, TipoNotificacao.REPUBLICACAO);

    private final NotificacaoRepository notificacoes;
    private final UsuarioService usuarios;
    private final PublicacaoService publicacoes;
    private final AnuncioService anuncios;
    private final CanalAoVivo aoVivo;

    @Transactional(readOnly = true)
    public PaginaNotificacoes listar(UUID quem, @Nullable String cursor) {
        CursorNotificacoes depois = CursorNotificacoes.ler(cursor);
        List<Grupo> grupos = notificacoes.grupos(quem, depois.em(), depois.chave(), TAMANHO_PAGINA + 1);
        boolean temMais = grupos.size() > TAMANHO_PAGINA;
        List<Grupo> pagina = temMais ? grupos.subList(0, TAMANHO_PAGINA) : grupos;
        String proximo = temMais ? CursorNotificacoes.depoisDe(pagina.getLast().em(), pagina.getLast().chave()) : null;
        Instant maisNovaEm = depois.inicio() && !pagina.isEmpty() ? pagina.getFirst().em() : null;
        return new PaginaNotificacoes(montar(quem, pagina), proximo, maisNovaEm);
    }

    /** Quantas notificações ainda não foram lidas. As curtidas do mesmo post contam como uma, como na tela. */
    @Transactional(readOnly = true)
    public long naoLidas(UUID quem) {
        return notificacoes.naoLidas(quem);
    }

    /**
     * Marca como lidas as que chegaram até {@code ate}, a data da mais nova que a tela mostrou. As outras abas da
     * pessoa acertam o contador ao vivo.
     *
     * @return quantas continuam não lidas: as que chegaram enquanto a tela carregava
     */
    @Transactional
    public long marcarLidas(UUID quem, Instant ate) {
        if (notificacoes.marcarLidas(quem, ate) > 0) {
            aoVivo.enviar(List.of(quem), EVENTO, Map.of());
        }
        return notificacoes.naoLidas(quem);
    }

    // ------------------------------------------------------------ apoio

    private List<NotificacaoVista> montar(UUID quem, List<Grupo> grupos) {
        Map<String, List<Linha>> linhasPorGrupo = notificacoes.linhas(quem, grupos.stream().map(Grupo::chave).toList(),
                        PESSOAS_POR_GRUPO).stream()
                .collect(Collectors.groupingBy(Linha::chave, LinkedHashMap::new, Collectors.toList()));
        List<Linha> linhas = linhasPorGrupo.values().stream().flatMap(List::stream).toList();

        Map<UUID, UsuarioPublico> pessoas = usuarios.buscarPublicos(linhas.stream().map(Linha::origem)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(UsuarioPublico::id, Function.identity()));
        Map<UUID, String> trechos = publicacoes.resumos(referencias(linhas, DE_PUBLICACAO)).stream()
                .collect(Collectors.toMap(ResumoPublicacao::id, ResumoPublicacao::trecho));
        Map<UUID, String> titulos = anuncios.resumos(referencias(linhas, EnumSet.of(TipoNotificacao.AVALIACAO_ANUNCIO)))
                .stream().collect(Collectors.toMap(ResumoAnuncio::id, ResumoAnuncio::titulo));

        List<NotificacaoVista> vistas = new ArrayList<>();
        for (Grupo grupo : grupos) {
            List<Linha> doGrupo = linhasPorGrupo.getOrDefault(grupo.chave(), List.of());
            if (doGrupo.isEmpty()) {
                continue;
            }
            Linha maisNova = doGrupo.getFirst();
            List<UsuarioPublico> quemFez = doGrupo.stream().map(Linha::origem).filter(Objects::nonNull)
                    .map(pessoas::get).filter(Objects::nonNull).toList();
            NotificacaoVista vista = vista(grupo, maisNova, quemFez, trechos, titulos);
            if (vista != null) {
                vistas.add(vista);
            }
        }
        return vistas;
    }

    /** {@code null} se o alvo saiu do ar ou se ninguém de quem fez aparece mais para os outros. */
    private static @Nullable NotificacaoVista vista(Grupo grupo, Linha linha, List<UsuarioPublico> pessoas,
            Map<UUID, String> trechos, Map<UUID, String> titulos) {
        if (pessoas.isEmpty()) {
            return null;
        }
        UUID publicacaoId = null;
        UUID anuncioId = null;
        String trecho = null;
        switch (linha.tipo()) {
            case SOLICITACAO_CONEXAO, CONEXAO_ACEITA, MODERACAO -> {
                // Levam ao perfil de quem fez ou aos pedidos de conexão.
            }
            case NOVO_COMENTARIO, RESPOSTA_COMENTARIO, CURTIDA_COMENTARIO -> {
                if (!trechos.containsKey(linha.referencia())) {
                    return null;
                }
                publicacaoId = linha.referencia();
                trecho = linha.trecho();
            }
            case CURTIDA_PUBLICACAO, REPUBLICACAO -> {
                trecho = trechos.get(linha.referencia());
                if (trecho == null) {
                    return null;
                }
                publicacaoId = linha.referencia();
            }
            case AVALIACAO_ANUNCIO -> {
                trecho = titulos.get(linha.referencia());
                if (trecho == null) {
                    return null;
                }
                anuncioId = linha.referencia();
            }
        }
        return new NotificacaoVista(grupo.chave(), linha.tipo().name(), grupo.em(), grupo.nova(), pessoas,
                grupo.total(), publicacaoId, anuncioId, trecho);
    }

    private static List<UUID> referencias(List<Linha> linhas, Set<TipoNotificacao> tipos) {
        return linhas.stream().filter(linha -> tipos.contains(linha.tipo())).map(Linha::referencia)
                .filter(Objects::nonNull).distinct().toList();
    }
}
