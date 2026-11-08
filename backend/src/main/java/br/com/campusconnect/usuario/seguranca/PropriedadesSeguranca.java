package br.com.campusconnect.usuario.seguranca;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.seguranca} do {@code application.yml}.
 *
 * @param jwtSegredo chave HMAC do JWT em Base64, com pelo menos 256 bits. Vazia gera uma chave aleatória
 *     na subida, e os JWTs emitidos deixam de valer quando a aplicação reinicia.
 * @param duracaoAcesso validade do JWT de acesso
 * @param duracaoRefresh validade do refresh token com "Manter conectado" marcado
 * @param duracaoRefreshSessao validade do refresh token sem "Manter conectado"
 * @param cookieSeguro marca o cookie do refresh token como {@code Secure} (só HTTPS ou localhost)
 */
@ConfigurationProperties("campusconnect.seguranca")
public record PropriedadesSeguranca(
        String jwtSegredo,
        @DefaultValue("15m") Duration duracaoAcesso,
        @DefaultValue("30d") Duration duracaoRefresh,
        @DefaultValue("1d") Duration duracaoRefreshSessao,
        @DefaultValue("true") boolean cookieSeguro) {

    public Duration duracaoRefresh(boolean persistente) {
        return persistente ? duracaoRefresh : duracaoRefreshSessao;
    }
}
