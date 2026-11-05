/** Notificações entregues aos usuários. */
@ApplicationModule(
        displayName = "Notificações",
        allowedDependencies = {"comum", "usuario :: service"})
package br.com.campusconnect.notificacao;

import org.springframework.modulith.ApplicationModule;
