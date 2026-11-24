package br.com.campusconnect.email.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloco {@code campusconnect.email} do {@code application.yml}. O servidor SMTP fica em {@code spring.mail}.
 *
 * @param remetente o "De:" dos e-mails, como {@code CampusConnect <nao-responda@exemplo.com>}. Em produção, o domínio
 *     precisa estar verificado no provedor (SPF e DKIM), senão os e-mails caem no spam.
 */
@ConfigurationProperties("campusconnect.email")
record PropriedadesEmail(
        @DefaultValue("CampusConnect <nao-responda@campusconnect.localhost>") String remetente) {
}
