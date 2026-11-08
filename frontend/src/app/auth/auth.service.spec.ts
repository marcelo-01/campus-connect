import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { SessaoResponse } from './sessao';

const SESSAO: SessaoResponse = {
  accessToken: 'jwt-1',
  expiraEm: '2026-11-08T23:00:00Z',
  usuario: { id: '1', username: 'ana', nomeCompleto: 'Ana Beatriz', nomeExibicao: null, email: 'ana@exemplo.com', fotoUrl: null },
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda o JWT e o usuário só em memória depois do login', () => {
    auth.entrar('ana@exemplo.com', 'senha-forte-123', true).subscribe();

    const requisicao = http.expectOne('/api/auth/login');
    expect(requisicao.request.body).toEqual({ email: 'ana@exemplo.com', senha: 'senha-forte-123', manterConectado: true });
    requisicao.flush(SESSAO);

    expect(auth.accessToken).toBe('jwt-1');
    expect(auth.usuario()?.username).toBe('ana');
    expect(auth.autenticado()).toBe(true);
    expect(JSON.stringify(localStorage)).not.toContain('jwt-1');
  });

  it('renovações simultâneas usam uma única requisição, porque o refresh token só vale uma vez', async () => {
    const primeira = auth.renovar();
    const segunda = auth.renovar();

    http.expectOne('/api/auth/refresh').flush(SESSAO);

    expect(await primeira).toBe(true);
    expect(await segunda).toBe(true);
    expect(auth.accessToken).toBe('jwt-1');
  });

  it('repete a renovação quando outra aba acabou de renovar (409)', async () => {
    const renovacao = auth.renovar();

    http.expectOne('/api/auth/refresh').flush({}, { status: 409, statusText: 'Conflict' });
    await new Promise((pronto) => setTimeout(pronto, 350));
    http.expectOne('/api/auth/refresh').flush(SESSAO);

    expect(await renovacao).toBe(true);
    expect(auth.autenticado()).toBe(true);
  });

  it('sem refresh token válido (401) a sessão fica vazia, sem repetir', async () => {
    const renovacao = auth.renovar();

    http.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(await renovacao).toBe(false);
    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken).toBeNull();
  });

  it('sair limpa a sessão mesmo se o servidor falhar', async () => {
    auth.entrar('ana@exemplo.com', 'x', false).subscribe();
    http.expectOne('/api/auth/login').flush(SESSAO);

    const saida = auth.sair();
    http.expectOne('/api/auth/logout').flush({}, { status: 500, statusText: 'Erro' });
    await saida;

    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken).toBeNull();
  });
});
