/** Conexões entre usuários. */
@ApplicationModule(
        displayName = "Conexões",
        allowedDependencies = {"comum", "usuario :: service"})
package br.com.campusconnect.conexao;

import org.springframework.modulith.ApplicationModule;
