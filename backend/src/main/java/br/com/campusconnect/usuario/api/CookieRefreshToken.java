package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.seguranca.PropriedadesSeguranca;
import br.com.campusconnect.usuario.service.SessaoEmitida;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie que guarda o refresh token no navegador.
 *
 * <ul>
 *   <li>{@code HttpOnly}: o JavaScript não lê o token, então um XSS não consegue roubá-lo.
 *   <li>{@code SameSite=Strict}: não vai em requisições disparadas por outro site (CSRF).
 *   <li>{@code Path=/api/auth}: só acompanha as rotas de refresh e logout.
 *   <li>Com "Manter conectado", tem {@code Max-Age}; sem, é cookie de sessão e some ao fechar o navegador.
 * </ul>
 */
@Component
@RequiredArgsConstructor
class CookieRefreshToken {

    static final String NOME = "cc_refresh";
    private static final String CAMINHO = "/api/auth";

    private final PropriedadesSeguranca propriedades;

    String criar(SessaoEmitida sessao) {
        ResponseCookie.ResponseCookieBuilder cookie = base(sessao.refreshToken());
        if (sessao.persistente()) {
            cookie.maxAge(Duration.between(Instant.now(), sessao.refreshTokenExpiraEm()));
        }
        return cookie.build().toString();
    }

    String apagar() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Strict")
                .path(CAMINHO);
    }
}
