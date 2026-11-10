/** Contas de usuário, sessão e perfil. */
@ApplicationModule(
        displayName = "Usuários",
        allowedDependencies = {"comum", "arquivo :: service"})
package br.com.campusconnect.usuario;

import org.springframework.modulith.ApplicationModule;
