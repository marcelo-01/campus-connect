package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import br.com.campusconnect.publicacao.repository.PublicacaoRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Curtir, salvar e republicar uma publicação. Ligar e desligar são idempotentes.
 *
 * <p>Curtir e republicar aparecem para os outros, então exigem ser membro da bolha do post. Salvar é
 * privado e vale para qualquer pessoa logada. Desligar nunca exige nada: quem saiu da bolha ainda pode
 * tirar a própria curtida.
 */
@Service
@RequiredArgsConstructor
public class InteracaoService {

    private final PublicacaoRepository publicacoes;
    private final InteracaoRepository interacoes;
    private final BolhaService bolhas;

    @Transactional
    public void curtir(UUID quem, UUID publicacaoId) {
        Publicacao publicacao = carregarPublicada(publicacaoId);
        RegrasPublicacao.garantirMembro(bolhas.ehMembro(quem, publicacao.getBolhaId()));
        interacoes.curtirPublicacao(quem, publicacaoId);
    }

    @Transactional
    public void descurtir(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        interacoes.descurtirPublicacao(quem, publicacaoId);
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
        RegrasPublicacao.garantirMembro(bolhas.ehMembro(quem, publicacao.getBolhaId()));
        interacoes.republicar(quem, publicacaoId);
    }

    @Transactional
    public void desfazerRepublicacao(UUID quem, UUID publicacaoId) {
        carregarPublicada(publicacaoId);
        interacoes.desfazerRepublicacao(quem, publicacaoId);
    }

    private Publicacao carregarPublicada(UUID id) {
        return publicacoes.findByIdAndStatus(id, StatusPublicacao.PUBLICADA)
                .orElseThrow(() -> new PublicacaoNaoEncontradaException(id));
    }
}
