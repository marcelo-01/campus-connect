package br.com.campusconnect.marketplace.api;

import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.marketplace.service.AnuncioDetalhe;
import br.com.campusconnect.marketplace.service.AnuncioService;
import br.com.campusconnect.marketplace.service.AvaliacaoVista;
import br.com.campusconnect.marketplace.service.CategoriaVista;
import br.com.campusconnect.marketplace.service.FiltroVitrine;
import br.com.campusconnect.marketplace.service.InteracaoAnuncioService;
import br.com.campusconnect.marketplace.service.PaginaAnuncios;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * O marketplace: a vitrine, os anúncios, os favoritos e as avaliações.
 *
 * <p>As listas são paginadas: cada página traz {@code proximo}, que o frontend devolve em {@code cursor}
 * para pedir a seguinte.
 */
@RestController
@RequestMapping("/api/anuncios")
@RequiredArgsConstructor
class AnuncioController {

    private final AnuncioService anuncios;
    private final InteracaoAnuncioService interacoes;

    // ------------------------------------------------------------ vitrine

    @GetMapping("/categorias")
    List<CategoriaVista> categorias() {
        return anuncios.categorias();
    }

    /**
     * @param escopo {@code bolhas} (padrão), {@code rede} ou {@code favoritos}
     * @param categoria o slug da categoria
     * @param q texto procurado no título e na descrição
     * @param ordem {@code recentes} (padrão), {@code preco-asc}, {@code preco-desc} ou {@code avaliacao}
     */
    @GetMapping
    PaginaAnuncios vitrine(@RequestParam(required = false) String escopo,
            @RequestParam(required = false) UUID bolhaId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String categoria, @RequestParam(required = false) String q,
            @RequestParam(required = false) String ordem, @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return anuncios.vitrine(idDe(jwt), new FiltroVitrine(escopo, bolhaId, tipo, categoria, q, ordem), cursor);
    }

    /** Os anúncios de uma pessoa. Ela vê também os pausados. */
    @GetMapping("/usuario/{usuarioId}")
    PaginaAnuncios doUsuario(@PathVariable UUID usuarioId, @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return anuncios.doUsuario(idDe(jwt), usuarioId, cursor);
    }

    @GetMapping("/{id}")
    AnuncioDetalhe buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return anuncios.buscar(idDe(jwt), id);
    }

    // ------------------------------------------------------------ anunciar e gerenciar

    /** Multipart: {@code dados} é o JSON do anúncio; {@code imagens} são as fotos (até 6). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AnuncioDetalhe> publicar(@Valid @RequestPart("dados") AnuncioRequest dados,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        AnuncioDetalhe publicado = anuncios.publicar(idDe(jwt), dados.paraDados(), conteudos(imagens));
        return ResponseEntity.created(URI.create("/api/anuncios/" + publicado.id())).body(publicado);
    }

    /**
     * Multipart, como ao anunciar. Em {@code dados}, {@code imagensMantidas} diz quais fotos atuais ficam;
     * as de {@code imagens} entram depois delas.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AnuncioDetalhe editar(@PathVariable UUID id, @Valid @RequestPart("dados") AnuncioRequest dados,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return anuncios.editar(idDe(jwt), id, dados.paraDados(), dados.imagensMantidas(), conteudos(imagens));
    }

    @PostMapping("/{id}/pausa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pausar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        anuncios.pausar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/pausa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        anuncios.reativar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        anuncios.excluir(idDe(jwt), id);
    }

    // ------------------------------------------------------------ favoritos e avaliações

    @PostMapping("/{id}/favorito")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favoritar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.favoritar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/favorito")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desfavoritar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.desfavoritar(idDe(jwt), id);
    }

    @GetMapping("/{id}/avaliacoes")
    List<AvaliacaoVista> avaliacoes(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return interacoes.avaliacoes(idDe(jwt), id);
    }

    /** Cria a avaliação de quem chama ou troca a que já existe. */
    @PutMapping("/{id}/avaliacao")
    AvaliacaoVista avaliar(@PathVariable UUID id, @Valid @RequestBody AvaliacaoRequest avaliacao,
            @AuthenticationPrincipal Jwt jwt) {
        return interacoes.avaliar(idDe(jwt), id, avaliacao.nota(), avaliacao.comentario());
    }

    @DeleteMapping("/{id}/avaliacoes/{avaliacaoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void apagarAvaliacao(@PathVariable UUID id, @PathVariable UUID avaliacaoId, @AuthenticationPrincipal Jwt jwt) {
        interacoes.apagarAvaliacao(idDe(jwt), id, avaliacaoId);
    }

    // ------------------------------------------------------------ apoio

    /** As fotos enviadas, já conferidas em até 5 MB cada. Partes vazias são ignoradas. */
    private static List<byte[]> conteudos(List<MultipartFile> imagens) throws IOException {
        List<byte[]> conteudos = new ArrayList<>();
        for (MultipartFile imagem : imagens == null ? List.<MultipartFile>of() : imagens) {
            if (!imagem.isEmpty()) {
                ImagemService.garantirTamanho(imagem.getSize());
                conteudos.add(imagem.getBytes());
            }
        }
        return conteudos;
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
