/**
 * Eventos ao vivo por SSE: uma conexão por aba visível, por onde os outros módulos avisam quem está logado (mensagens
 * novas, leituras e, depois, notificações).
 */
@ApplicationModule(
        displayName = "Ao vivo",
        allowedDependencies = {"comum"})
package br.com.campusconnect.aovivo;

import org.springframework.modulith.ApplicationModule;
