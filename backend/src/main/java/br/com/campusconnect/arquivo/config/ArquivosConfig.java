package br.com.campusconnect.arquivo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PropriedadesArquivos.class)
class ArquivosConfig {
}
