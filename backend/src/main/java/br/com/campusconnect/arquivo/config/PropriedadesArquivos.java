package br.com.campusconnect.arquivo.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.arquivos} do {@code application.yml}.
 *
 * @param diretorio pasta onde as imagens ficam gravadas. Caminho relativo vale a partir da pasta em
 *     que a aplicação foi iniciada.
 */
@ConfigurationProperties("campusconnect.arquivos")
public record PropriedadesArquivos(@DefaultValue("uploads") Path diretorio) {
}
