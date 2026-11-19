package br.com.campusconnect.notificacao.service;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import br.com.campusconnect.conexao.service.ConexaoService;
import br.com.campusconnect.conexao.service.EventosConexao;
import br.com.campusconnect.marketplace.service.EventosMarketplace;
import br.com.campusconnect.notificacao.model.TipoNotificacao;
import br.com.campusconnect.notificacao.repository.NotificacaoRepository;
import br.com.campusconnect.notificacao.repository.NotificacaoRepository.Nova;
import br.com.campusconnect.publicacao.service.EventosPublicacao;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transforma o que acontece nos outros módulos em notificações, e apaga as que perderam o sentido: a curtida
 * desfeita antes de ser vista, o pedido retirado, o comentário excluído, a publicação ou o anúncio que saiu do ar.
 *
 * <p>Cada evento é tratado depois do commit de quem agiu, numa transação própria. Assim, uma notificação que falha
 * não desfaz a curtida ou o comentário: o Spring só registra o erro no log, porque o ouvinte roda no
 * {@code afterCompletion} da transação, que não repassa exceções.
 *
 * <p>Ninguém é avisado do que fez a si mesmo, e nada passa entre duas pessoas com bloqueio, em qualquer sentido.
 */
@Component
@RequiredArgsConstructor
class Notificador {

    /** Em caracteres (um emoji conta como um), como o trecho das publicações. */
    static final int TAMANHO_TRECHO = 140;

    private final NotificacaoRepository notificacoes;
    private final ConexaoService conexoes;
    private final CanalAoVivo aoVivo;

    // ------------------------------------------------------------ conexões

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoPedir(EventosConexao.PedidoFeito evento) {
        avisar(evento.destinatario(), evento.solicitante(), TipoNotificacao.SOLICITACAO_CONEXAO, null, null, null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAceitar(EventosConexao.PedidoAceito evento) {
        avisar(evento.solicitante(), evento.quemAceitou(), TipoNotificacao.CONEXAO_ACEITA, null, null, null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRetirarPedido(EventosConexao.PedidoRetirado evento) {
        mudou(notificacoes.apagarPedido(evento.destinatario(), evento.solicitante()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoBloquear(EventosConexao.PessoaBloqueada evento) {
        mudou(notificacoes.apagarEntre(evento.quem(), evento.bloqueado()));
    }

    // ------------------------------------------------------------ publicações

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCurtir(EventosPublicacao.PublicacaoCurtida evento) {
        avisar(evento.autorId(), evento.quem(), TipoNotificacao.CURTIDA_PUBLICACAO, evento.publicacaoId(), null, null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDescurtir(EventosPublicacao.CurtidaRetirada evento) {
        mudou(notificacoes.apagarNaoLidaDaReferencia(evento.quem(), TipoNotificacao.CURTIDA_PUBLICACAO,
                evento.publicacaoId()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRepublicar(EventosPublicacao.PublicacaoRepublicada evento) {
        avisar(evento.autorId(), evento.quem(), TipoNotificacao.REPUBLICACAO, evento.publicacaoId(), null, null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDesfazerRepublicacao(EventosPublicacao.RepublicacaoDesfeita evento) {
        mudou(notificacoes.apagarNaoLidaDaReferencia(evento.quem(), TipoNotificacao.REPUBLICACAO,
                evento.publicacaoId()));
    }

    /**
     * Quem foi respondido é avisado pela resposta. Quem escreveu a publicação é avisado de todo comentário nela, menos
     * quando é a própria pessoa respondida: aí basta o aviso da resposta.
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoComentar(EventosPublicacao.ComentarioFeito evento) {
        String trecho = trecho(evento.conteudo());
        if (evento.respondendoA() != null) {
            avisar(evento.respondendoA(), evento.autorId(), TipoNotificacao.RESPOSTA_COMENTARIO, evento.publicacaoId(),
                    evento.comentarioId(), trecho);
        }
        if (!evento.autorDaPublicacao().equals(evento.respondendoA())) {
            avisar(evento.autorDaPublicacao(), evento.autorId(), TipoNotificacao.NOVO_COMENTARIO,
                    evento.publicacaoId(), evento.comentarioId(), trecho);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCurtirComentario(EventosPublicacao.ComentarioCurtido evento) {
        avisar(evento.autorDoComentario(), evento.quem(), TipoNotificacao.CURTIDA_COMENTARIO, evento.publicacaoId(),
                evento.comentarioId(), trecho(evento.conteudo()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDescurtirComentario(EventosPublicacao.CurtidaDeComentarioRetirada evento) {
        mudou(notificacoes.apagarNaoLidaDoDetalhe(evento.quem(), TipoNotificacao.CURTIDA_COMENTARIO,
                evento.comentarioId()));
    }

    /** Leva o comentário, a curtida nele e, se ele era uma resposta, o aviso da resposta. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoExcluirComentario(EventosPublicacao.ComentarioExcluido evento) {
        mudou(notificacoes.apagarDoDetalhe(evento.comentarioId()));
    }

    /** Leva tudo o que apontava para as publicações: curtidas, republicações e comentários. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoExcluirPublicacoes(EventosPublicacao.PublicacoesExcluidas evento) {
        mudou(notificacoes.apagarDasReferencias(evento.publicacaoIds()));
    }

    // ------------------------------------------------------------ marketplace

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAvaliar(EventosMarketplace.AnuncioAvaliado evento) {
        avisar(evento.anuncianteId(), evento.autorId(), TipoNotificacao.AVALIACAO_ANUNCIO, evento.anuncioId(),
                evento.avaliacaoId(), null);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoApagarAvaliacao(EventosMarketplace.AvaliacaoApagada evento) {
        mudou(notificacoes.apagarDoDetalhe(evento.avaliacaoId()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoExcluirAnuncio(EventosMarketplace.AnuncioExcluido evento) {
        mudou(notificacoes.apagarDasReferencias(List.of(evento.anuncioId())));
    }

    // ------------------------------------------------------------ apoio

    private void avisar(UUID destinatario, UUID origem, TipoNotificacao tipo, @Nullable UUID referencia,
            @Nullable UUID detalhe, @Nullable String trecho) {
        if (destinatario.equals(origem) || conexoes.haBloqueio(destinatario, origem)) {
            return;
        }
        if (notificacoes.gravar(new Nova(destinatario, origem, tipo, referencia, detalhe, trecho))) {
            mudou(List.of(destinatario));
        }
    }

    /** O contador dessas pessoas mudou. O aviso sai depois do commit desta transação. */
    private void mudou(List<UUID> destinatarios) {
        if (!destinatarios.isEmpty()) {
            aoVivo.enviar(destinatarios, NotificacaoService.EVENTO, Map.of());
        }
    }

    /** O começo do comentário, numa linha só. */
    static String trecho(String conteudo) {
        String linha = conteudo.replaceAll("\\s+", " ").strip();
        if (linha.codePointCount(0, linha.length()) <= TAMANHO_TRECHO) {
            return linha;
        }
        return linha.substring(0, linha.offsetByCodePoints(0, TAMANHO_TRECHO)).stripTrailing() + "…";
    }
}
