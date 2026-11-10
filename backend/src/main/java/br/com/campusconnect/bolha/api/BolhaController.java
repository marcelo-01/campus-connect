package br.com.campusconnect.bolha.api;

import br.com.campusconnect.bolha.service.BolhaDetalhe;
import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import br.com.campusconnect.bolha.service.MembroDaBolha;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A página de uma bolha é lida pelo slug, que é o que aparece na URL do site; as ações usam o id. Criar,
 * editar, trocar a foto e excluir são só para administradores (conferido em {@link BolhaService}).
 */
@RestController
@RequestMapping("/api/bolhas")
@RequiredArgsConstructor
class BolhaController {

    private final BolhaService bolhas;

    @GetMapping
    List<BolhaResumo> todas(@AuthenticationPrincipal Jwt jwt) {
        return bolhas.listarTodas(idDe(jwt));
    }

    /** Bolhas de um usuário: aparecem no perfil e no menu lateral. */
    @GetMapping("/usuario/{usuarioId}")
    List<BolhaResumo> doUsuario(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return bolhas.listarDoUsuario(usuarioId, idDe(jwt));
    }

    @GetMapping("/{slug}")
    ResponseEntity<BolhaDetalhe> detalhe(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(bolhas.buscar(slug, idDe(jwt)));
    }

    @GetMapping("/{id}/membros")
    List<MembroDaBolha> membros(@PathVariable UUID id) {
        return bolhas.listarMembros(id);
    }

    @PostMapping("/{id}/participacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void entrar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        bolhas.entrar(idDe(jwt), id);
    }

    @DeleteMapping("/{id}/participacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sair(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        bolhas.sair(idDe(jwt), id);
    }

    @PostMapping
    ResponseEntity<BolhaDetalhe> criar(@Valid @RequestBody NovaBolhaRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {
        BolhaDetalhe criada = bolhas.criar(idDe(jwt), requisicao.paraNovaBolha());
        return ResponseEntity.created(URI.create("/api/bolhas/" + criada.slug())).body(criada);
    }

    @PutMapping("/{id}")
    BolhaDetalhe atualizar(@PathVariable UUID id, @Valid @RequestBody AlteracaoBolhaRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {
        return bolhas.atualizar(idDe(jwt), id, requisicao.paraAlteracao());
    }

    @PostMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BolhaDetalhe trocarFoto(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return bolhas.trocarFoto(idDe(jwt), id, arquivo.getBytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        bolhas.excluir(idDe(jwt), id);
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
