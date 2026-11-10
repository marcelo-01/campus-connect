import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { administradorGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { UsuarioResumo } from './sessao';

function conta(administrador: boolean): UsuarioResumo {
  return {
    id: '1',
    username: 'ana',
    nomeCompleto: 'Ana',
    nomeExibicao: null,
    email: 'ana@exemplo.com',
    fotoUrl: null,
    administrador,
  };
}

function rodarGuard(usuario: UsuarioResumo | null): boolean | UrlTree {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { usuario: signal(usuario) } },
    ],
  });
  return TestBed.runInInjectionContext(
    () =>
      administradorGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as
        boolean | UrlTree,
  );
}

describe('administradorGuard', () => {
  it('deixa o administrador abrir as telas de criar e editar bolha', () => {
    expect(rodarGuard(conta(true))).toBe(true);
  });

  it('manda quem não é administrador para o Explorar', () => {
    const resultado = rodarGuard(conta(false));

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/bolhas');
  });
});
