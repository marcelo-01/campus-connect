/** Denúncias e sua análise. */
@ApplicationModule(
        displayName = "Moderação",
        allowedDependencies = {"comum", "marketplace :: service", "publicacao :: service", "usuario :: service"})
package br.com.campusconnect.moderacao;

import org.springframework.modulith.ApplicationModule;
