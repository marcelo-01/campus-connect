/** Conversas e mensagens privadas entre duas pessoas (RF05). */
@ApplicationModule(
        displayName = "Conversas",
        allowedDependencies = {"comum", "usuario :: service", "conexao :: service", "aovivo :: service"})
package br.com.campusconnect.conversa;

import org.springframework.modulith.ApplicationModule;
