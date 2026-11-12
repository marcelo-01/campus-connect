import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Só deixa passar quem está logado; os outros vão para o login. */
export const autenticadoGuard: CanActivateFn = () =>
  inject(AuthService).autenticado() || inject(Router).createUrlTree(['/entrar']);

/**
 * Telas de criar e editar bolhas. Quem não é administrador vai para o Explorar. É só conveniência: o
 * backend recusa a ação de qualquer forma.
 */
export const administradorGuard: CanActivateFn = () =>
  inject(AuthService).usuario()?.administrador === true || inject(Router).createUrlTree(['/bolhas']);

/** Login e cadastro são só para quem ainda não entrou; quem já está logado vai para o início. */
export const visitanteGuard: CanActivateFn = () =>
  !inject(AuthService).autenticado() || inject(Router).createUrlTree(['/']);

/**
 * `/perfil/<o próprio username>/conexoes` vai para `/conexoes`, onde a pessoa também vê os pedidos. O username é
 * comparado como o backend guarda: sem o `@` da frente e em minúsculas.
 */
export const conexoesDeOutraPessoaGuard: CanActivateFn = (rota) => {
  const pedido = (rota.paramMap.get('username') ?? '').trim().replace(/^@/, '').toLowerCase();
  return (
    pedido !== inject(AuthService).usuario()?.username || inject(Router).createUrlTree(['/conexoes'])
  );
};
