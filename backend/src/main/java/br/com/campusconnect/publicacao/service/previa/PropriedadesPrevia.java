package br.com.campusconnect.publicacao.service.previa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.previa-link} do {@code application.yml}.
 *
 * @param permitirRedeLocal deixa buscar prévias em endereços internos (localhost, rede privada). Só os
 *     testes ligam, para usar um servidor HTTP local. Em produção fica desligado: é a proteção contra SSRF.
 */
@ConfigurationProperties("campusconnect.previa-link")
public record PropriedadesPrevia(@DefaultValue("false") boolean permitirRedeLocal) {
}
