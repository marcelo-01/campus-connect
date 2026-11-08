package br.com.campusconnect.arquivo.api;

import br.com.campusconnect.arquivo.service.ImagemService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega as imagens sem exigir login: a tag {@code <img>} do navegador não manda o JWT. O nome
 * aleatório de 128 bits faz as vezes de senha.
 */
@RestController
@RequestMapping("/api/arquivos")
@RequiredArgsConstructor
class ArquivoController {

    private final ImagemService imagens;

    @GetMapping("/{nome}")
    ResponseEntity<Resource> baixar(@PathVariable String nome) {
        return imagens.localizar(nome)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // O conteúdo de um nome nunca muda; trocar a foto gera outro nome.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body((Resource) new FileSystemResource(arquivo)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
