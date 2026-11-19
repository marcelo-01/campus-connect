package br.com.campusconnect.notificacao.api;

import br.com.campusconnect.notificacao.service.NotificacaoService;
import br.com.campusconnect.notificacao.service.PaginaNotificacoes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As notificações de quem está logado. Quando o contador muda, a conexão de {@code /api/aovivo} manda o evento
 * {@value NotificacaoService#EVENTO}, em todas as abas da pessoa.
 */
@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
class NotificacaoController {

    private final NotificacaoService notificacoes;

    @GetMapping
    PaginaNotificacoes listar(@RequestParam(required = false) @Nullable String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return notificacoes.listar(idDe(jwt), cursor);
    }

    @GetMapping("/nao-lidas")
    Total naoLidas(@AuthenticationPrincipal Jwt jwt) {
        return new Total(notificacoes.naoLidas(idDe(jwt)));
    }

    /**
     * Marca como lidas as que chegaram até {@code ate}, o {@code maisNovaEm} da primeira página, e devolve quantas
     * continuam não lidas.
     */
    @PostMapping("/leitura")
    Total lerAte(@Valid @RequestBody LeituraRequest dados, @AuthenticationPrincipal Jwt jwt) {
        return new Total(notificacoes.marcarLidas(idDe(jwt), dados.ate()));
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record LeituraRequest(@NotNull(message = "Informe até quando as notificações foram lidas.") Instant ate) {
    }

    record Total(long total) {
    }
}
