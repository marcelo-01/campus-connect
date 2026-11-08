/** Publicações, comentários, mídias e reações. */
@ApplicationModule(
        displayName = "Publicações",
        allowedDependencies = {"comum", "bolha :: service", "usuario :: service"})
package br.com.campusconnect.publicacao;

import org.springframework.modulith.ApplicationModule;
