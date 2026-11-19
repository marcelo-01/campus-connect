/**
 * Notificações entregues aos usuários. Nascem dos eventos dos módulos de conexões, publicações e marketplace, que não
 * sabem que este módulo existe, e chegam ao vivo pela conexão do módulo {@code aovivo}.
 */
@ApplicationModule(
        displayName = "Notificações",
        allowedDependencies = {"comum", "usuario :: service", "conexao :: service", "publicacao :: service",
                "marketplace :: service", "aovivo :: service"})
package br.com.campusconnect.notificacao;

import org.springframework.modulith.ApplicationModule;
