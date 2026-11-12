package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.bolha.service.BolhaExcluida;
import br.com.campusconnect.publicacao.repository.InteracaoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Quando uma bolha é excluída, apaga de vez as publicações dela, com comentários, curtidas, salvas e
 * republicações. Roda na mesma transação da exclusão: se ela falhar, nada é apagado. Os arquivos saem
 * do disco só depois do commit.
 */
@Component
@RequiredArgsConstructor
class ApagaPublicacoesDaBolha {

    private final InteracaoRepository interacoes;
    private final ArquivoService arquivos;

    @EventListener
    void aoExcluir(BolhaExcluida evento) {
        List<String> arquivosDaBolha = interacoes.arquivosDasPublicacoesDaBolha(evento.bolhaId());
        interacoes.apagarPublicacoesDaBolha(evento.bolhaId());
        arquivos.descartarAoFim(arquivosDaBolha, List.of());
    }
}
