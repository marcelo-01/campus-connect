/**
 * E-mails mandados pela aplicação: os códigos de verificação do cadastro e de redefinição de senha e o aviso de senha
 * alterada. Cada um nasce de um evento do módulo de usuários e sai pelo SMTP configurado em {@code spring.mail}.
 */
@ApplicationModule(
        displayName = "E-mail",
        allowedDependencies = {"usuario :: service"})
package br.com.campusconnect.email;

import org.springframework.modulith.ApplicationModule;
