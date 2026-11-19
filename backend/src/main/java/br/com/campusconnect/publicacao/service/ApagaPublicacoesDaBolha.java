package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.bolha.service.BolhaExcluida;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Quando uma bolha é excluída, apaga de vez as publicações dela, com comentários, curtidas, salvas e
 * republicações. Roda na mesma transação da exclusão: se ela falhar, nada é apagado. Os arquivos saem
 * do disco só depois do commit.
 *
 * <p>Avisa os outros módulos com {@link EventosPublicacao.PublicacoesExcluidas}, como na exclusão de um post.
 */
@Component
@RequiredArgsConstructor
class ApagaPublicacoesDaBolha {

    private final InteracaoRepository interacoes;
    private final ArquivoService arquivos;
    private final ApplicationEventPublisher eventos;

    @EventListener
    void aoExcluir(BolhaExcluida evento) {
        List<UUID> publicacoes = interacoes.publicacoesDaBolha(evento.bolhaId());
        List<String> arquivosDaBolha = interacoes.arquivosDasPublicacoesDaBolha(evento.bolhaId());
        interacoes.apagarPublicacoesDaBolha(evento.bolhaId());
        arquivos.descartarAoFim(arquivosDaBolha, List.of());
        if (!publicacoes.isEmpty()) {
            eventos.publishEvent(new EventosPublicacao.PublicacoesExcluidas(publicacoes));
        }
    }
}
