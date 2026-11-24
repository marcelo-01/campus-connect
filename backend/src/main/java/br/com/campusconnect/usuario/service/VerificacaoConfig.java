package br.com.campusconnect.usuario.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesVerificacao.class)
class VerificacaoConfig {
}
