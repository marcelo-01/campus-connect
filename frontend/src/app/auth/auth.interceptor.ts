import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Coloca o JWT de acesso nas chamadas à API. Se o JWT vencer (401), renova pelo refresh token e repete
 * a chamada uma vez. Se a renovação falhar, a sessão acabou e a pessoa volta para o login.
 *
 * As rotas `/api/auth/**` ficam de fora: elas não usam o JWT, e um 401 nelas não deve disparar renovação.
 */
export const authInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  if (!requisicao.url.startsWith('/api/') || requisicao.url.startsWith('/api/auth/')) {
    return proximo(requisicao);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  return proximo(comToken(requisicao, auth.accessToken)).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401) {
        return throwError(() => erro);
      }
      return from(auth.renovar()).pipe(
        switchMap((renovou) => {
          if (!renovou) {
            void router.navigateByUrl('/entrar');
            return throwError(() => erro);
          }
          return proximo(comToken(requisicao, auth.accessToken));
        }),
      );
    }),
  );
};

function comToken(requisicao: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : requisicao;
}
