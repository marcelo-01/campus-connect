package br.com.campusconnect.email.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async}, que usa o {@code applicationTaskExecutor} do Spring Boot. Assim, a conversa com o servidor
 * SMTP não segura a resposta do cadastro nem a do login.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(PropriedadesEmail.class)
class EmailConfig {
}
