/** Bolhas, a hierarquia entre elas e seus membros. */
@ApplicationModule(
        displayName = "Bolhas",
        allowedDependencies = {"comum", "usuario :: service", "arquivo :: service"})
package br.com.campusconnect.bolha;

import org.springframework.modulith.ApplicationModule;
