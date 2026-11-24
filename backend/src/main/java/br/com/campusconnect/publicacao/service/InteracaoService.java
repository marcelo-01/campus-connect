package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import br.com.campusconnect.publicacao.repository.PublicacaoRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Curtir, salvar e republicar uma publicação. Ligar e desligar são idempotentes.
 *
 * <p>Curtir e republicar aparecem para os outros, então exigem ser membro de uma das bolhas do post.
 * Salvar é privado e vale para qualquer pessoa logada. Desligar nunca exige nada: quem saiu da bolha ainda
 * pode tirar a própria curtida.
 *
 * <p>Curtir e republicar avisam quem escreveu pelos {@link EventosPublicacao}, só quando a ação muda algo.
 */
@Service
@RequiredArgsConstructor
public class InteracaoService {

    private final PublicacaoRepository publicacoes;
    private final InteracaoRepository interacoes;
    private final BolhaService bolhas;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public void curtir(UUID quem, UUID publicacaoId) {
        Publicacao publicacao = carregarPublicada(publicacaoId);
        RegrasPublicacao.garantirMembro(bolhas.ehMembroDeAlguma(quem, publicacao.getBolhaIds()));
        if (interacoes.curtirPublicacao(quem, publicacaoId)) {
            eventos.publishEvent(new EventosPublicacao.PublicacaoCurtida(quem, publicacaoId, publicacao.getAutorId()));
        }
    }

    @Transactional
    public void descurtir(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        if (interacoes.descurtirPublicacao(quem, publicacaoId)) {
            eventos.publishEvent(new EventosPublicacao.CurtidaRetirada(quem, publicacaoId));
        }
    }

    @Transactional
    public void salvar(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        interacoes.salvar(quem, publicacaoId);
    }

    @Transactional
    public void deixarDeSalvar(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        interacoes.deixarDeSalvar(quem, publicacaoId);
    }

    /** O post passa a aparecer no perfil de quem republicou. */
    @Transactional
    public void republicar(UUID quem, UUID publicacaoId) {
        Publicacao publicacao = carregarPublicada(publicacaoId);
        if (publicacao.getAutorId().equals(quem)) {
            throw new CampoInvalidoException("publicacao", RegrasPublicacao.NAO_REPUBLICA_O_PROPRIO);
        }
        RegrasPublicacao.garantirMembro(bolhas.ehMembroDeAlguma(quem, publicacao.getBolhaIds()));
        if (interacoes.republicar(quem, publicacaoId)) {
            eventos.publishEvent(
                    new EventosPublicacao.PublicacaoRepublicada(quem, publicacaoId, publicacao.getAutorId()));
        }
    }

    @Transactional
    public void desfazerRepublicacao(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        if (interacoes.desfazerRepublicacao(quem, publicacaoId)) {
            eventos.publishEvent(new EventosPublicacao.RepublicacaoDesfeita(quem, publicacaoId));
        }
    }

    private Publicacao carregarPublicada(UUID id) {
        return publicacoes.findByIdAndStatus(id, StatusPublicacao.PUBLICADA)
                .orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }
}
