package br.com.campusconnect.arquivo.api;

import br.com.campusconnect.arquivo.service.ArquivoService;
import java.nio.file.Path;
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
 * Entrega imagens e vídeos sem exigir login: as tags {@code <img>} e {@code <video>} do navegador não
 * mandam o JWT. O nome aleatório de 128 bits faz as vezes de senha.
 *
 * <p>Devolver um {@link Resource} faz o Spring atender pedidos com {@code Range} (resposta 206). É o que
 * deixa a pessoa avançar o vídeo sem baixar tudo antes.
 */
@RestController
@RequestMapping("/api/arquivos")
@RequiredArgsConstructor
class ArquivoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");
    private static final MediaType VIDEO_WEBM = MediaType.parseMediaType("video/webm");

    private final ArquivoService arquivos;

    @GetMapping("/{nome}")
    ResponseEntity<Resource> baixar(@PathVariable String nome) {
        return arquivos.localizar(nome)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(tipo(arquivo))
                        // O conteúdo de um nome nunca muda; trocar o arquivo gera outro nome.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body((Resource) new FileSystemResource(arquivo)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** O {@link ArquivoService#localizar} só aceita estas três extensões. */
    private static MediaType tipo(Path arquivo) {
        String nome = arquivo.getFileName().toString();
        if (nome.endsWith(".mp4")) {
            return VIDEO_MP4;
        }
        if (nome.endsWith(".webm")) {
            return VIDEO_WEBM;
        }
        return MediaType.IMAGE_JPEG;
    }
}
