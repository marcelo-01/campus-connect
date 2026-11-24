package br.com.campusconnect.usuario.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.verificacao} do {@code application.yml}: o código mandado para o e-mail no cadastro.
 *
 * @param validadeCodigo quanto tempo o código vale depois de mandado
 * @param tentativasPorCodigo quantos códigos errados a pessoa pode digitar antes de o código deixar de valer
 * @param intervaloReenvio o tempo mínimo entre dois códigos para a mesma conta
 * @param enviosPorHora quantos códigos a mesma conta recebe por hora
 */
@ConfigurationProperties("campusconnect.verificacao")
record PropriedadesVerificacao(
        @DefaultValue("15m") Duration validadeCodigo,
        @DefaultValue("5") int tentativasPorCodigo,
        @DefaultValue("60s") Duration intervaloReenvio,
        @DefaultValue("5") int enviosPorHora) {
}
