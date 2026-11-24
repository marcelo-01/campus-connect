import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { SessaoResponse } from './sessao';

const SESSAO: SessaoResponse = {
  accessToken: 'jwt-1',
  expiraEm: '2026-11-08T23:00:00Z',
  usuario: { id: '1', username: 'ana', nomeCompleto: 'Ana Beatriz', nomeExibicao: null, email: 'ana@exemplo.com', fotoUrl: null, administrador: false },
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

  it('o cadastro não abre a sessão: ela só abre quando o código do e-mail confere', () => {
    const novo = { nomeCompleto: 'Ana Beatriz', username: 'ana', email: 'ana@exemplo.com', senha: 'senha-forte-123' };
    let pendente: unknown;
    auth.cadastrar(novo).subscribe((resposta) => (pendente = resposta));

    const cadastro = http.expectOne('/api/auth/cadastro');
    expect(cadastro.request.body).toEqual(novo);
    cadastro.flush({ email: 'ana@exemplo.com' }, { status: 201, statusText: 'Created' });
    expect(pendente).toEqual({ email: 'ana@exemplo.com' });
    expect(auth.autenticado()).toBe(false);

    auth.verificarEmail('ana@exemplo.com', '123456').subscribe();
    const verificacao = http.expectOne('/api/auth/verificar-email');
    expect(verificacao.request.body).toEqual({ email: 'ana@exemplo.com', codigo: '123456' });
    verificacao.flush(SESSAO);

    expect(auth.accessToken).toBe('jwt-1');
    expect(auth.usuario()?.username).toBe('ana');
  });

  it('pede outro código pelo e-mail', () => {
    auth.reenviarCodigo('ana@exemplo.com').subscribe();

    const reenvio = http.expectOne('/api/auth/reenviar-codigo');
    expect(reenvio.request.method).toBe('POST');
    expect(reenvio.request.body).toEqual({ email: 'ana@exemplo.com' });
    reenvio.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('redefine a senha pelo código: confere antes, sem abrir a sessão, e entra no fim', () => {
    auth.esqueciSenha('ana@exemplo.com').subscribe();
    const pedido = http.expectOne('/api/auth/esqueci-senha');
    expect(pedido.request.body).toEqual({ email: 'ana@exemplo.com' });
    pedido.flush(null, { status: 202, statusText: 'Accepted' });

    auth.conferirCodigoRedefinicao('ana@exemplo.com', '123456').subscribe();
    const conferencia = http.expectOne('/api/auth/redefinir-senha/conferir');
    expect(conferencia.request.body).toEqual({ email: 'ana@exemplo.com', codigo: '123456' });
    conferencia.flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.autenticado()).toBe(false);

    auth.redefinirSenha('ana@exemplo.com', '123456', 'outra-senha-forte').subscribe();
    const redefinicao = http.expectOne('/api/auth/redefinir-senha');
    expect(redefinicao.request.body).toEqual({
      email: 'ana@exemplo.com',
      codigo: '123456',
      novaSenha: 'outra-senha-forte',
    });
    redefinicao.flush(SESSAO);

    expect(auth.accessToken).toBe('jwt-1');
    expect(auth.usuario()?.username).toBe('ana');
  });

  it('alterar a senha troca a sessão pela nova que o servidor devolve', () => {
    auth.entrar('ana@exemplo.com', 'senha-forte-123', true).subscribe();
    http.expectOne('/api/auth/login').flush(SESSAO);

    auth.alterarSenha('senha-forte-123', 'outra-senha-forte').subscribe();
    const alteracao = http.expectOne('/api/auth/alterar-senha');
    expect(alteracao.request.body).toEqual({ senhaAtual: 'senha-forte-123', novaSenha: 'outra-senha-forte' });
    alteracao.flush({ ...SESSAO, accessToken: 'jwt-2' });

    expect(auth.accessToken).toBe('jwt-2');
  });

  it('alterar a senha com a sessão já vencida (401) esquece a sessão local', () => {
    auth.entrar('ana@exemplo.com', 'senha-forte-123', true).subscribe();
    http.expectOne('/api/auth/login').flush(SESSAO);

    auth.alterarSenha('senha-forte-123', 'outra-senha-forte').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/alterar-senha').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken).toBeNull();
  });

  it('alterar a senha com a senha atual errada (400) não mexe na sessão', () => {
    auth.entrar('ana@exemplo.com', 'senha-forte-123', true).subscribe();
    http.expectOne('/api/auth/login').flush(SESSAO);

    auth.alterarSenha('errada', 'outra-senha-forte').subscribe({ error: () => undefined });
    http
      .expectOne('/api/auth/alterar-senha')
      .flush({ erros: { senhaAtual: 'A senha atual está incorreta.' } }, { status: 400, statusText: 'Bad Request' });

    expect(auth.accessToken).toBe('jwt-1');
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
