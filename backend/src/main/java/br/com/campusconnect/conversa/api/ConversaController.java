package br.com.campusconnect.conversa.api;

import br.com.campusconnect.conversa.service.ConversaService;
import br.com.campusconnect.conversa.service.ConversaVista;
import br.com.campusconnect.conversa.service.MensagemVista;
import br.com.campusconnect.conversa.service.PaginaConversas;
import br.com.campusconnect.conversa.service.PaginaMensagens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mensagens privadas (RF05). As mudanças também chegam ao vivo pela conexão de {@code /api/aovivo}, para as duas
 * pessoas e em todas as abas.
 */
@RestController
@RequestMapping("/api/conversas")
@RequiredArgsConstructor
class ConversaController {

    private final ConversaService conversas;

    /** Abre a conversa com a pessoa: a que já existe ou uma nova, vazia. */
    @PostMapping("/com/{usuarioId}")
    ConversaVista abrirCom(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return conversas.abrirCom(idDe(jwt), usuarioId);
    }

    /** @param filtro {@code nao-lidas} para só as conversas com mensagem não lida */
    @GetMapping
    PaginaConversas listar(@RequestParam(required = false) @Nullable String cursor,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable String filtro,
            @AuthenticationPrincipal Jwt jwt) {
        return conversas.listar(idDe(jwt), cursor, q, "nao-lidas".equals(filtro));
    }

    @GetMapping("/nao-lidas")
    Total naoLidas(@AuthenticationPrincipal Jwt jwt) {
        return new Total(conversas.totalNaoLidas(idDe(jwt)));
    }

    @GetMapping("/{id}")
    ConversaVista buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return conversas.buscar(idDe(jwt), id);
    }

    @GetMapping("/{id}/mensagens")
    PaginaMensagens mensagens(@PathVariable UUID id, @RequestParam(required = false) @Nullable String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return conversas.mensagens(idDe(jwt), id, cursor);
    }

    @PostMapping("/{id}/mensagens")
    ResponseEntity<MensagemVista> enviar(@PathVariable UUID id, @RequestBody NovaMensagemRequest dados,
            @AuthenticationPrincipal Jwt jwt) {
        MensagemVista mensagem = conversas.enviar(idDe(jwt), id, dados.conteudo());
        return ResponseEntity.created(URI.create("/api/conversas/" + id + "/mensagens/" + mensagem.id()))
                .body(mensagem);
    }

    /** Marca a conversa como lida até a mensagem {@code ate}. */
    @PostMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void lerAte(@PathVariable UUID id, @Valid @RequestBody LeituraRequest dados, @AuthenticationPrincipal Jwt jwt) {
        conversas.lerAte(idDe(jwt), id, dados.ate());
    }

    @DeleteMapping("/{id}/mensagens/{mensagemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void apagar(@PathVariable UUID id, @PathVariable UUID mensagemId, @AuthenticationPrincipal Jwt jwt) {
        conversas.apagar(idDe(jwt), id, mensagemId);
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** O texto é conferido no serviço, com a mesma mensagem de erro para vazio e só espaços. */
    record NovaMensagemRequest(@Nullable String conteudo) {
    }

    record LeituraRequest(@NotNull(message = "Informe até qual mensagem a conversa foi lida.") UUID ate) {
    }

    record Total(long total) {
    }
}
