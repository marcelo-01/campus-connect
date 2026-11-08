package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.AutenticacaoService;
import br.com.campusconnect.usuario.service.SessaoEmitida;
import br.com.campusconnect.usuario.service.SessaoInvalidaException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AutenticacaoController {

    private final AutenticacaoService autenticacao;
    private final CookieRefreshToken cookie;

    @PostMapping("/cadastro")
    ResponseEntity<SessaoResponse> cadastrar(@Valid @RequestBody CadastroRequest requisicao) {
        return comCookie(HttpStatus.CREATED, autenticacao.cadastrar(requisicao.paraNovoUsuario()));
    }

    @PostMapping("/login")
    ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        return comCookie(HttpStatus.OK,
                autenticacao.entrar(requisicao.email(), requisicao.senha(), requisicao.manterConectado()));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessaoResponse> renovar(@CookieValue(name = CookieRefreshToken.NOME, required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new SessaoInvalidaException();
        }
        return comCookie(HttpStatus.OK, autenticacao.renovar(token));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> sair(@CookieValue(name = CookieRefreshToken.NOME, required = false) String token) {
        if (token != null && !token.isBlank()) {
            autenticacao.sair(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.apagar()).build();
    }

    private ResponseEntity<SessaoResponse> comCookie(HttpStatus status, SessaoEmitida sessao) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.criar(sessao))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(SessaoResponse.de(sessao));
    }
}
