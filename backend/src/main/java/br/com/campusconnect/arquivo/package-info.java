/** Imagens enviadas pelos usuários: validação, gravação em disco e entrega por {@code /api/arquivos}. */
@ApplicationModule(
        displayName = "Arquivos",
        allowedDependencies = "comum")
package br.com.campusconnect.arquivo;

import org.springframework.modulith.ApplicationModule;
