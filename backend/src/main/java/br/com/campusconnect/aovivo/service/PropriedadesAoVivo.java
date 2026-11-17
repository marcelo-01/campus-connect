package br.com.campusconnect.aovivo.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.aovivo} do {@code application.yml}. Os testes encurtam os tempos.
 *
 * @param duracaoMaxima quanto uma conexão dura no máximo. Ela também nunca passa da validade do JWT que a abriu: o
 *     cliente reconecta com um JWT novo, e uma sessão encerrada para de receber eventos.
 * @param pulso o intervalo do comentário vazio que mantém a conexão viva em proxies que cortam conexões paradas
 * @param maximoPorPessoa quantas conexões (abas) uma pessoa mantém ao mesmo tempo; a mais antiga sai
 */
@ConfigurationProperties("campusconnect.aovivo")
record PropriedadesAoVivo(
        @DefaultValue("30m") Duration duracaoMaxima,
        @DefaultValue("25s") Duration pulso,
        @DefaultValue("5") int maximoPorPessoa) {
}
