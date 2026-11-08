/** Contas de usuário e vínculos institucionais. */
@ApplicationModule(
        displayName = "Usuários",
        allowedDependencies = {"comum", "instituicao :: service", "arquivo :: service"})
package br.com.campusconnect.usuario;

import org.springframework.modulith.ApplicationModule;
