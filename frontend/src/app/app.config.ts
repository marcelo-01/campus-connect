import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';
import { AparenciaService } from './layout/aparencia/aparencia.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideRouter(routes),
    // Antes da primeira rota: se o cookie de refresh ainda vale, a pessoa continua logada após um F5.
    provideAppInitializer(() => inject(AuthService).restaurarSessao()),
    // Logo na subida, para o tema escolhido valer também na tela de login.
    provideAppInitializer(() => {
      inject(AparenciaService);
    }),
  ],
};
