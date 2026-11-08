package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.SessaoEmitida;
import br.com.campusconnect.usuario.service.UsuarioResumo;
import java.time.Instant;

/** O que o frontend recebe ao entrar. O refresh token não aparece aqui: ele vai só no cookie HttpOnly. */
record SessaoResponse(String accessToken, Instant expiraEm, UsuarioResumo usuario) {

    static SessaoResponse de(SessaoEmitida sessao) {
        return new SessaoResponse(sessao.accessToken(), sessao.accessTokenExpiraEm(), sessao.usuario());
    }
}
