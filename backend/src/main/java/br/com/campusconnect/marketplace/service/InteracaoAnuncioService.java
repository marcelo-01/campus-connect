package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.marketplace.model.Anuncio;
import br.com.campusconnect.marketplace.model.AvaliacaoAnuncio;
import br.com.campusconnect.marketplace.model.StatusAnuncio;
import br.com.campusconnect.marketplace.repository.AnuncioRepository;
import br.com.campusconnect.marketplace.repository.AvaliacaoRepository;
import br.com.campusconnect.marketplace.repository.InteracaoAnuncioRepository;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Favoritar e avaliar anúncios. Só valem para anúncios publicados.
 *
 * <p>Qualquer pessoa logada, menos quem anunciou, deixa uma avaliação por anúncio, que pode trocar ou
 * apagar depois. O pagamento acontece fora da plataforma, então o sistema não sabe quem de fato contratou
 * ou comprou.
 */
@Service
@RequiredArgsConstructor
public class InteracaoAnuncioService {

    private final AnuncioRepository anuncios;
    private final AvaliacaoRepository avaliacoes;
    private final InteracaoAnuncioRepository interacoes;
    private final MontadorAnuncios montador;
    private final UsuarioService usuarios;

    @Transactional
    public void favoritar(UUID quem, UUID anuncioId) {
        carregarPublicado(anuncioId);
        interacoes.favoritar(quem, anuncioId);
    }

    /** Desfavoritar nunca exige nada: quem favoritou pode tirar o favorito mesmo de um anúncio pausado. */
    @Transactional
    public void desfavoritar(UUID quem, UUID anuncioId) {
        interacoes.desfavoritar(quem, anuncioId);
    }

    /**
     * Grava a avaliação de quem chama, ou troca a que já existe.
     *
     * @throws AcessoNegadoException se quem chama for quem anunciou
     */
    @Transactional
    public AvaliacaoVista avaliar(UUID quem, UUID anuncioId, @Nullable Integer nota, @Nullable String comentario) {
        Anuncio anuncio = carregarPublicado(anuncioId);
        if (anuncio.getAnuncianteId().equals(quem)) {
            throw new AcessoNegadoException(RegrasAnuncio.NAO_AVALIA_O_PROPRIO);
        }
        interacoes.avaliar(quem, anuncioId, RegrasAnuncio.nota(nota), RegrasAnuncio.comentario(comentario));
        AvaliacaoAnuncio gravada = avaliacoes.findByAnuncio_IdAndAutorId(anuncioId, quem)
                .orElseThrow(() -> new AnuncioNaoEncontradoException(anuncioId));
        return montador.avaliacoes(List.of(gravada), quem).stream().findFirst()
                .orElseThrow(() -> new AnuncioNaoEncontradoException(anuncioId));
    }

    /** Quem avaliou apaga a própria avaliação; um administrador apaga qualquer uma. */
    @Transactional
    public void apagarAvaliacao(UUID quem, UUID anuncioId, UUID avaliacaoId) {
        AvaliacaoAnuncio avaliacao = avaliacoes.findByIdAndAnuncio_Id(avaliacaoId, anuncioId)
                .orElseThrow(() -> new AvaliacaoNaoEncontradaException(avaliacaoId));
        if (!avaliacao.getAutorId().equals(quem) && !usuarios.ehAdministrador(quem)) {
            throw new AcessoNegadoException(RegrasAnuncio.SO_QUEM_AVALIOU_APAGA);
        }
        avaliacoes.delete(avaliacao);
    }

    /**
     * As avaliações de um anúncio que quem vê pode ver, das mais novas para as mais antigas.
     *
     * @throws AnuncioNaoEncontradoException nos mesmos casos da página do anúncio
     */
    @Transactional(readOnly = true)
    public List<AvaliacaoVista> avaliacoes(UUID quemVe, UUID anuncioId) {
        Anuncio anuncio = anuncios.findById(anuncioId)
                .filter(encontrado -> encontrado.getStatus() == StatusAnuncio.PUBLICADO
                        || (encontrado.getStatus() == StatusAnuncio.PAUSADO
                                && encontrado.getAnuncianteId().equals(quemVe)))
                .orElseThrow(() -> new AnuncioNaoEncontradoException(anuncioId));
        return montador.avaliacoes(avaliacoes.findTop100ByAnuncio_IdOrderByCriadoEmDescIdDesc(anuncio.getId()),
                quemVe);
    }

    private Anuncio carregarPublicado(UUID id) {
        return anuncios.findById(id).filter(anuncio -> anuncio.getStatus() == StatusAnuncio.PUBLICADO)
                .orElseThrow(() -> new AnuncioNaoEncontradoException(id));
    }
}
