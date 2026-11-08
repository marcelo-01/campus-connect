import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { SessaoResponse } from './sessao';

function sessao(token: string): SessaoResponse {
  return {
    accessToken: token,
    expiraEm: '2026-11-08T23:00:00Z',
    usuario: { id: '1', username: 'ana', nomeCompleto: 'Ana', nomeExibicao: null, email: 'ana@exemplo.com', fotoUrl: null },
  };
}

const proximoCiclo = () => new Promise((pronto) => setTimeout(pronto));

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    TestBed.inject(AuthService).entrar('ana@exemplo.com', 'x', true).subscribe();
    backend.expectOne('/api/auth/login').flush(sessao('jwt-antigo'));
  });

  afterEach(() => backend.verify());

  it('manda o JWT nas rotas da API', () => {
    http.get('/api/usuarios/me').subscribe();

    const requisicao = backend.expectOne('/api/usuarios/me');
    expect(requisicao.request.headers.get('Authorization')).toBe('Bearer jwt-antigo');
    requisicao.flush({});
  });

  it('não manda o JWT nas rotas de autenticação', () => {
    http.post('/api/auth/logout', null).subscribe();

    const requisicao = backend.expectOne('/api/auth/logout');
    expect(requisicao.request.headers.has('Authorization')).toBe(false);
    requisicao.flush(null);
  });

  it('com o JWT vencido (401), renova e repete a chamada uma vez', async () => {
    let resposta: unknown;
    http.get('/api/usuarios/me').subscribe((corpo) => (resposta = corpo));

    backend.expectOne('/api/usuarios/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush(sessao('jwt-novo'));
    await proximoCiclo();

    const repetida = backend.expectOne('/api/usuarios/me');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer jwt-novo');
    repetida.flush({ username: 'ana' });

    expect(resposta).toEqual({ username: 'ana' });
  });

  it('se a renovação falhar, volta para o login', async () => {
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    let falhou = false;
    http.get('/api/usuarios/me').subscribe({ error: () => (falhou = true) });

    backend.expectOne('/api/usuarios/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await proximoCiclo();

    expect(falhou).toBe(true);
    expect(navegar).toHaveBeenCalledWith('/entrar');
    expect(TestBed.inject(AuthService).autenticado()).toBe(false);
  });
});
