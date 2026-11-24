/**
 * E-mails mandados pela aplicação. Por enquanto é só o código de verificação do cadastro, que nasce de um evento do
 * módulo de usuários e sai pelo SMTP configurado em {@code spring.mail}.
 */
@ApplicationModule(
        displayName = "E-mail",
        allowedDependencies = {"usuario :: service"})
package br.com.campusconnect.email;

import org.springframework.modulith.ApplicationModule;
