package br.com.campusconnect.conversa.service;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.conexao.service.ConexaoService;
import br.com.campusconnect.conversa.repository.ConversaRepository;
import br.com.campusconnect.conversa.repository.ConversaRepository.Participacao;
import br.com.campusconnect.conversa.repository.MensagemRepository;
import br.com.campusconnect.conversa.service.RegrasConversa.Par;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversas diretas entre duas pessoas (RF05). Qualquer pessoa logada pode conversar com qualquer outra, desde que não
 * haja bloqueio entre as duas.
 *
 * <p>Quem não participa de uma conversa recebe 404, e nunca 403, para não ficar sabendo que ela existe. A conversa com
 * uma conta que não aparece mais para os outros (suspensa, banida, desativada ou anonimizada) some da lista e também
 * dá 404.
 *
 * <p>Cada mudança vai ao vivo para as duas pessoas, em todas as abas, depois do commit: {@value #EVENTO_MENSAGEM},
 * {@value #EVENTO_LEITURA} e {@value #EVENTO_MENSAGEM_APAGADA}.
 */
@Service
@RequiredArgsConstructor
public class ConversaService {

    public static final String EVENTO_MENSAGEM = "mensagem";
    public static final String EVENTO_LEITURA = "leitura";
    public static final String EVENTO_MENSAGEM_APAGADA = "mensagem-apagada";

    static final int TAMANHO_PAGINA_CONVERSAS = 30;
    static final int TAMANHO_PAGINA_MENSAGENS = 30;

    /** O texto é neutro: quem foi bloqueado fica sabendo só que não dá para mandar mensagens. */
    static final String COM_BLOQUEIO = "Não é possível enviar mensagens para esta pessoa.";

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final UsuarioService usuarios;
    private final ConexaoService conexoes;
    private final CanalAoVivo aoVivo;
    private final PropriedadesConversa limites;

    // ------------------------------------------------------------ conversas

    /**
     * Abre a conversa com a outra pessoa: devolve a que já existe ou cria uma nova, vazia. A conversa vazia não aparece
     * na lista de ninguém até a primeira mensagem.
     *
     * <p>Com bloqueio em qualquer sentido, uma conversa nova não é criada. A que já existe abre, para o histórico
     * continuar acessível, mas sem envio.
     *
     * @throws ConversaConsigoMesmoException se {@code quem} e {@code outro} forem a mesma pessoa
     * @throws PessoaIndisponivelException se a outra conta não existe ou não aparece para os outros
     * @throws AcessoNegadoException se a conversa é nova e há bloqueio entre as duas pessoas
     */
    @Transactional
    public ConversaVista abrirCom(UUID quem, UUID outro) {
        Par par = Par.de(quem, outro);
        UsuarioPublico pessoa = publica(outro).orElseThrow(() -> new PessoaIndisponivelException(outro));
        UUID id = conversas.idDoPar(par.menor(), par.maior()).orElseGet(() -> {
            if (conexoes.haBloqueio(quem, outro)) {
                throw new AcessoNegadoException(COM_BLOQUEIO);
            }
            return conversas.criarDireta(par.menor(), par.maior());
        });
        Participacao participacao = conversas.participacao(id, quem).orElseThrow();
        return vista(quem, participacao, pessoa);
    }

    /** @throws ConversaNaoEncontradaException se a conversa não existe ou não é de {@code quem} */
    @Transactional(readOnly = true)
    public ConversaVista buscar(UUID quem, UUID conversaId) {
        Aberta aberta = aberta(quem, conversaId);
        return vista(quem, aberta.participacao(), aberta.pessoa());
    }

    /**
     * As conversas com mensagem, da última mensagem mais recente para a mais antiga.
     *
     * @param termo procura no nome e no username da outra pessoa
     * @param soNaoLidas só as conversas com mensagem não lida
     */
    @Transactional(readOnly = true)
    public PaginaConversas listar(UUID quem, @Nullable String cursor, @Nullable String termo, boolean soNaoLidas) {
        CursorConversa depoisDe = CursorConversa.ler(cursor);
        // As contas que não aparecem saem antes da paginação, para nenhuma página vir curta.
        Set<UUID> outros = usuarios.publicosEntre(conversas.outrosDe(quem), termo);
        List<ConversaRepository.Linha> linhas = conversas.listar(quem, outros, soNaoLidas,
                depoisDe == null ? null : depoisDe.em(), depoisDe == null ? null : depoisDe.id(),
                TAMANHO_PAGINA_CONVERSAS + 1);

        boolean temMais = linhas.size() > TAMANHO_PAGINA_CONVERSAS;
        List<ConversaRepository.Linha> pagina = temMais ? linhas.subList(0, TAMANHO_PAGINA_CONVERSAS) : linhas;
        Map<UUID, UsuarioPublico> pessoas = publicasPorId(pagina.stream().map(ConversaRepository.Linha::outroId)
                .toList());
        List<ConversaResumo> itens = pagina.stream()
                .filter(linha -> pessoas.containsKey(linha.outroId()))
                .map(linha -> new ConversaResumo(linha.conversaId(), pessoas.get(linha.outroId()),
                        new ConversaResumo.Previa(linha.mensagemId(), RegrasConversa.previa(linha.conteudo()),
                                linha.remetenteId().equals(quem), linha.enviadoEm(), linha.apagada()),
                        linha.naoLida()))
                .toList();

        ConversaRepository.Linha ultima = pagina.isEmpty() ? null : pagina.getLast();
        String proximo = temMais && ultima != null
                ? CursorConversa.depoisDe(ultima.ultimaMensagemEm(), ultima.conversaId()) : null;
        return new PaginaConversas(itens, proximo);
    }

    /** Quantas conversas têm mensagem não lida: o número do item "Mensagens" do menu. Bate com a lista. */
    @Transactional(readOnly = true)
    public long totalNaoLidas(UUID quem) {
        return conversas.contarNaoLidas(quem, usuarios.publicosEntre(conversas.outrosDe(quem), null));
    }

    // ------------------------------------------------------------ mensagens

    /**
     * Uma página da conversa, da mensagem mais nova para a mais antiga.
     *
     * @throws ConversaNaoEncontradaException se a conversa não existe ou não é de {@code quem}
     */
    @Transactional(readOnly = true)
    public PaginaMensagens mensagens(UUID quem, UUID conversaId, @Nullable String cursor) {
        CursorConversa antesDe = CursorConversa.ler(cursor);
        aberta(quem, conversaId);
        List<MensagemRepository.Linha> linhas = mensagens.pagina(conversaId,
                antesDe == null ? null : antesDe.em(), antesDe == null ? null : antesDe.id(),
                TAMANHO_PAGINA_MENSAGENS + 1);

        boolean temMais = linhas.size() > TAMANHO_PAGINA_MENSAGENS;
        List<MensagemRepository.Linha> pagina = temMais ? linhas.subList(0, TAMANHO_PAGINA_MENSAGENS) : linhas;
        List<MensagemVista> itens = pagina.stream().map(linha -> vista(conversaId, linha)).toList();
        String proximo = temMais ? CursorConversa.depoisDe(pagina.getLast().enviadoEm(), pagina.getLast().id()) : null;
        return new PaginaMensagens(itens, proximo);
    }

    /**
     * Manda uma mensagem. Para quem mandou, a conversa fica lida até ela.
     *
     * <p>Uma mensagem enviada no mesmo instante de um bloqueio pode passar: a conferência e a gravação não travam o
     * par, como faz o módulo de conexões.
     *
     * @throws ConversaNaoEncontradaException se a conversa não existe, não é de {@code quem} ou a outra conta sumiu
     * @throws AcessoNegadoException se há bloqueio entre as duas pessoas
     * @throws LimiteDeEnvioException se {@code quem} passou do limite de mensagens ou de conversas novas
     */
    @Transactional
    public MensagemVista enviar(UUID quem, UUID conversaId, @Nullable String conteudo) {
        String texto = RegrasConversa.conteudo(conteudo);
        Aberta aberta = aberta(quem, conversaId);
        UUID outro = aberta.participacao().outroId();
        if (conexoes.haBloqueio(quem, outro)) {
            throw new AcessoNegadoException(COM_BLOQUEIO);
        }
        garantirLimite(quem, aberta.participacao());

        MensagemRepository.Linha gravada = mensagens.inserir(conversaId, quem, texto);
        conversas.registrarEnvio(conversaId, gravada.enviadoEm());
        conversas.avancarLeitura(conversaId, quem, gravada.enviadoEm());

        MensagemVista vista = vista(conversaId, gravada);
        aoVivo.enviar(List.of(quem, outro), EVENTO_MENSAGEM, vista);
        return vista;
    }

    /**
     * Marca a conversa como lida até a mensagem dada. A leitura nunca volta.
     *
     * @throws ConversaNaoEncontradaException se a conversa não existe ou não é de {@code quem}
     */
    @Transactional
    public void lerAte(UUID quem, UUID conversaId, UUID mensagemId) {
        Aberta aberta = aberta(quem, conversaId);
        conversas.lerAte(conversaId, quem, mensagemId).ifPresent(ate -> aoVivo.enviar(
                List.of(quem, aberta.participacao().outroId()), EVENTO_LEITURA,
                new EventoLeitura(conversaId, quem, ate)));
    }

    /**
     * Apaga a mensagem para as duas pessoas. Só quem mandou pode apagar; apagar de novo não muda nada.
     *
     * @throws ConversaNaoEncontradaException se a conversa não existe ou não é de {@code quem}
     * @throws MensagemNaoEncontradaException se a mensagem não é desta conversa
     * @throws AcessoNegadoException se a mensagem é da outra pessoa
     */
    @Transactional
    public void apagar(UUID quem, UUID conversaId, UUID mensagemId) {
        Aberta aberta = aberta(quem, conversaId);
        MensagemRepository.Linha mensagem = mensagens.buscar(conversaId, mensagemId)
                .orElseThrow(() -> new MensagemNaoEncontradaException(mensagemId));
        if (!mensagem.remetenteId().equals(quem)) {
            throw new AcessoNegadoException("Só quem mandou a mensagem pode apagá-la.");
        }
        if (mensagens.apagar(conversaId, mensagemId, quem) == 1) {
            aoVivo.enviar(List.of(quem, aberta.participacao().outroId()), EVENTO_MENSAGEM_APAGADA,
                    new EventoMensagemApagada(conversaId, mensagemId));
        }
    }

    // ------------------------------------------------------------ apoio

    /** A conversa de que {@code quem} participa, com a outra pessoa ainda visível. */
    private record Aberta(Participacao participacao, UsuarioPublico pessoa) {
    }

    private Aberta aberta(UUID quem, UUID conversaId) {
        Participacao participacao = conversas.participacao(conversaId, quem)
                .orElseThrow(() -> new ConversaNaoEncontradaException(conversaId));
        UsuarioPublico pessoa = publica(participacao.outroId())
                .orElseThrow(() -> new ConversaNaoEncontradaException(conversaId));
        return new Aberta(participacao, pessoa);
    }

    private ConversaVista vista(UUID quem, Participacao participacao, UsuarioPublico pessoa) {
        boolean bloqueei = conexoes.bloqueou(quem, pessoa.id());
        boolean podeEnviar = !bloqueei && !conexoes.bloqueou(pessoa.id(), quem);
        return new ConversaVista(participacao.conversaId(), pessoa, podeEnviar, bloqueei, participacao.outroLeuEm());
    }

    private static MensagemVista vista(UUID conversaId, MensagemRepository.Linha linha) {
        return new MensagemVista(linha.id(), conversaId, linha.remetenteId(), linha.conteudo(), linha.enviadoEm(),
                linha.apagada());
    }

    /** O limite contra spam. A conversa ainda vazia conta como uma conversa nova de quem manda a primeira mensagem. */
    private void garantirLimite(UUID quem, Participacao participacao) {
        if (mensagens.enviadasNosUltimos(quem, Duration.ofMinutes(1)) >= limites.mensagensPorMinuto()) {
            throw new LimiteDeEnvioException();
        }
        if (participacao.ultimaMensagemEm() == null
                && mensagens.conversasComecadasNosUltimos(quem, Duration.ofDays(1)) >= limites.conversasNovasPorDia()) {
            throw new LimiteDeEnvioException();
        }
    }

    private Optional<UsuarioPublico> publica(UUID id) {
        return usuarios.buscarPublicos(List.of(id)).stream().findFirst();
    }

    private Map<UUID, UsuarioPublico> publicasPorId(List<UUID> ids) {
        return usuarios.buscarPublicos(ids).stream()
                .collect(Collectors.toMap(UsuarioPublico::id, Function.identity(), (uma, outra) -> uma));
    }
}
