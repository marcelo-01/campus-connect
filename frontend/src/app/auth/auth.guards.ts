import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Só deixa passar quem está logado; os outros vão para o login. */
export const autenticadoGuard: CanActivateFn = () =>
  inject(AuthService).autenticado() || inject(Router).createUrlTree(['/entrar']);

/** Login e cadastro são só para quem ainda não entrou; quem já está logado vai para o início. */
export const visitanteGuard: CanActivateFn = () =>
  !inject(AuthService).autenticado() || inject(Router).createUrlTree(['/']);
