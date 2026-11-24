package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.AutenticacaoService;
import br.com.campusconnect.usuario.service.CadastroPendente;
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

    /** Não abre a sessão: ela só abre depois que o código mandado para o e-mail confere. */
    @PostMapping("/cadastro")
    ResponseEntity<CadastroPendente> cadastrar(@Valid @RequestBody CadastroRequest requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(autenticacao.cadastrar(requisicao.paraNovoUsuario()));
    }

    @PostMapping("/verificar-email")
    ResponseEntity<SessaoResponse> verificarEmail(@Valid @RequestBody CodigoRequest requisicao) {
        return comCookie(HttpStatus.OK, autenticacao.verificarEmail(requisicao.email(), requisicao.codigo()));
    }

    /** Responde 202 também para e-mail sem conta, para não dizer quem tem conta pendente. */
    @PostMapping("/reenviar-codigo")
    ResponseEntity<Void> reenviarCodigo(@Valid @RequestBody EmailRequest requisicao) {
        autenticacao.reenviarCodigo(requisicao.email());
        return ResponseEntity.accepted().build();
    }

    /** Também responde 202 para e-mail sem conta, para não dizer quem tem conta. */
    @PostMapping("/esqueci-senha")
    ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EmailRequest requisicao) {
        autenticacao.esqueciSenha(requisicao.email());
        return ResponseEntity.accepted().build();
    }

    /** Só confere o código, para a tela pedir a senha nova depois. O código continua valendo. */
    @PostMapping("/redefinir-senha/conferir")
    ResponseEntity<Void> conferirCodigoRedefinicao(@Valid @RequestBody CodigoRequest requisicao) {
        autenticacao.conferirCodigoRedefinicao(requisicao.email(), requisicao.codigo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redefinir-senha")
    ResponseEntity<SessaoResponse> redefinirSenha(@Valid @RequestBody RedefinicaoSenhaRequest requisicao) {
        return comCookie(HttpStatus.OK,
                autenticacao.redefinirSenha(requisicao.email(), requisicao.codigo(), requisicao.novaSenha()));
    }

    /**
     * A pessoa logada é reconhecida pelo cookie, como no refresh, e não pelo JWT. Assim a resposta já traz o cookie
     * da sessão nova deste aparelho, que continua logado enquanto os outros saem.
     */
    @PostMapping("/alterar-senha")
    ResponseEntity<SessaoResponse> alterarSenha(
            @CookieValue(name = CookieRefreshToken.NOME, required = false) String token,
            @Valid @RequestBody AlteracaoSenhaRequest requisicao) {
        if (token == null || token.isBlank()) {
            throw new SessaoInvalidaException();
        }
        return comCookie(HttpStatus.OK,
                autenticacao.alterarSenha(token, requisicao.senhaAtual(), requisicao.novaSenha()));
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
