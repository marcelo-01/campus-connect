/** Anúncios, avaliações, favoritos e impulsionamentos. */
@ApplicationModule(
        displayName = "Marketplace",
        allowedDependencies = {"comum", "bolha :: service", "usuario :: service"})
package br.com.campusconnect.marketplace;

import org.springframework.modulith.ApplicationModule;
