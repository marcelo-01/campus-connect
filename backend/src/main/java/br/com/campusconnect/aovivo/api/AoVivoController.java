package br.com.campusconnect.aovivo.api;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * A conexão SSE de quem está logado. O navegador abre por {@code fetch}, porque o {@code EventSource} não manda o JWT
 * no cabeçalho.
 */
@RestController
@RequestMapping("/api/aovivo")
@RequiredArgsConstructor
class AoVivoController {

    private final CanalAoVivo canal;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter abrir(@AuthenticationPrincipal Jwt jwt, HttpServletResponse resposta) {
        // Um nginx na frente seguraria os eventos num buffer sem isso.
        resposta.setHeader("X-Accel-Buffering", "no");
        resposta.setHeader("Cache-Control", "no-cache");
        return canal.abrir(UUID.fromString(jwt.getSubject()), jwt.getExpiresAt());
    }
}
