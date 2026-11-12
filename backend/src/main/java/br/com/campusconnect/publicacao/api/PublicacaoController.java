package br.com.campusconnect.publicacao.api;

import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.publicacao.service.ComentarioService;
import br.com.campusconnect.publicacao.service.ComentarioVista;
import br.com.campusconnect.publicacao.service.InteracaoService;
import br.com.campusconnect.publicacao.service.PaginaFeed;
import br.com.campusconnect.publicacao.service.PublicacaoService;
import br.com.campusconnect.publicacao.service.PublicacaoVista;
import br.com.campusconnect.publicacao.service.VideoEnviado;
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
 * Publicações, feeds, curtidas, salvas, republicações e comentários.
 *
 * <p>Os feeds são paginados: cada página traz {@code proximo}, que o frontend devolve em {@code cursor}
 * para pedir a seguinte. {@code ordem} é um dos chips: {@code recentes}, {@code alta} ou {@code midia}.
 */
@RestController
@RequestMapping("/api/publicacoes")
@RequiredArgsConstructor
class PublicacaoController {

    private final PublicacaoService publicacoes;
    private final InteracaoService interacoes;
    private final ComentarioService comentarios;

    // ------------------------------------------------------------ feeds

    /** Sem {@code bolhaId}: os posts das bolhas da pessoa. Com: só os feitos nessa bolha. */
    @GetMapping("/feed")
    PaginaFeed feed(@RequestParam(required = false) UUID bolhaId, @RequestParam(required = false) String ordem,
            @RequestParam(required = false) String cursor, @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.feedInicio(idDe(jwt), bolhaId, ordem, cursor);
    }

    /** Os posts da bolha e os das bolhas acima dela. */
    @GetMapping("/bolha/{bolhaId}")
    PaginaFeed daBolha(@PathVariable UUID bolhaId, @RequestParam(required = false) String ordem,
            @RequestParam(required = false) String cursor, @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.feedBolha(idDe(jwt), bolhaId, ordem, cursor);
    }

    /** Os posts de uma pessoa e os que ela republicou. */
    @GetMapping("/usuario/{usuarioId}")
    PaginaFeed doUsuario(@PathVariable UUID usuarioId, @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.feedUsuario(idDe(jwt), usuarioId, cursor);
    }

    @GetMapping("/salvas")
    PaginaFeed salvas(@RequestParam(required = false) String cursor, @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.salvas(idDe(jwt), cursor);
    }

    // ------------------------------------------------------------ publicação

    @GetMapping("/{id}")
    PublicacaoVista buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.buscar(idDe(jwt), id);
    }

    /**
     * Multipart: {@code dados} é o JSON com a bolha, o texto e o link; {@code imagens} (até 4) ou
     * {@code video} (1) são os arquivos.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PublicacaoVista> publicar(@Valid @RequestPart("dados") NovaPublicacaoRequest dados,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens,
            @RequestPart(name = "video", required = false) MultipartFile video,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        List<byte[]> conteudos = new ArrayList<>();
        for (MultipartFile imagem : imagens == null ? List.<MultipartFile>of() : imagens) {
            if (!imagem.isEmpty()) {
                ImagemService.garantirTamanho(imagem.getSize());
                conteudos.add(imagem.getBytes());
            }
        }
        VideoEnviado enviado = video == null || video.isEmpty() ? null : new VideoEnviado(video.getSize(), video);
        PublicacaoVista publicada = publicacoes.publicar(idDe(jwt), dados.paraNovaPublicacao(), conteudos, enviado);
        return ResponseEntity.created(URI.create("/api/publicacoes/" + publicada.id())).body(publicada);
    }

    /** Só o texto muda. */
    @PutMapping("/{id}")
    PublicacaoVista editar(@PathVariable UUID id, @Valid @RequestBody EdicaoPublicacaoRequest edicao,
            @AuthenticationPrincipal Jwt jwt) {
        return publicacoes.editar(idDe(jwt), id, edicao.conteudo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        publicacoes.excluir(idDe(jwt), id);
    }

    // ------------------------------------------------------------ curtir, salvar e republicar

    @PostMapping("/{id}/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void curtir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.curtir(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void descurtir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.descurtir(idDe(jwt), id);
    }

    @PostMapping("/{id}/salva")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void salvar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.salvar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/salva")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deixarDeSalvar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.deixarDeSalvar(idDe(jwt), id);
    }

    @PostMapping("/{id}/republicacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void republicar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.republicar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/republicacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desfazerRepublicacao(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        interacoes.desfazerRepublicacao(idDe(jwt), id);
    }

    // ------------------------------------------------------------ comentários

    /** @param ordem {@code relevantes} (padrão) ou {@code recentes} */
    @GetMapping("/{id}/comentarios")
    List<ComentarioVista> comentarios(@PathVariable UUID id, @RequestParam(required = false) String ordem,
            @AuthenticationPrincipal Jwt jwt) {
        return comentarios.listar(idDe(jwt), id, ordem);
    }

    @PostMapping("/{id}/comentarios")
    ResponseEntity<ComentarioVista> comentar(@PathVariable UUID id, @Valid @RequestBody NovoComentarioRequest novo,
            @AuthenticationPrincipal Jwt jwt) {
        ComentarioVista comentario = comentarios.comentar(idDe(jwt), id, novo.conteudo(), novo.respondendoA());
        return ResponseEntity.created(URI.create("/api/publicacoes/" + id + "/comentarios")).body(comentario);
    }

    @DeleteMapping("/comentarios/{comentarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluirComentario(@PathVariable UUID comentarioId, @AuthenticationPrincipal Jwt jwt) {
        comentarios.excluir(idDe(jwt), comentarioId);
    }

    @PostMapping("/comentarios/{comentarioId}/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void curtirComentario(@PathVariable UUID comentarioId, @AuthenticationPrincipal Jwt jwt) {
        comentarios.curtir(idDe(jwt), comentarioId);
    }

    @DeleteMapping("/comentarios/{comentarioId}/curtida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void descurtirComentario(@PathVariable UUID comentarioId, @AuthenticationPrincipal Jwt jwt) {
        comentarios.descurtir(idDe(jwt), comentarioId);
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
