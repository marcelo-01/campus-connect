package br.com.campusconnect.usuario.service;

import java.time.Instant;

/**
 * Resultado de um login, cadastro ou renovação.
 *
 * <p>O {@code accessToken} vai no corpo da resposta e o {@code refreshToken} só no cookie HttpOnly.
 */
public record SessaoEmitida(
        String accessToken,
        Instant accessTokenExpiraEm,
        String refreshToken,
        Instant refreshTokenExpiraEm,
        boolean persistente,
        UsuarioResumo usuario) {

    @Override
    public String toString() {
        return "SessaoEmitida[usuario=" + usuario.id() + ", persistente=" + persistente + "]";
    }
}
