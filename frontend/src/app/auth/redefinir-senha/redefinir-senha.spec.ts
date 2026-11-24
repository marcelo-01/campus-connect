import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ToastService } from '../../shared/toast.service';
import { AuthService } from '../auth.service';
import { UsuarioResumo } from '../sessao';
import { RedefinicaoPendente } from './redefinicao-pendente';
import { RedefinirSenha } from './redefinir-senha';

const ANA: UsuarioResumo = {
  id: 'ana',
  username: 'ana',
  nomeCompleto: 'Ana Beatriz',
  nomeExibicao: null,
  email: 'ana@exemplo.com',
  fotoUrl: null,
  administrador: false,
};

function erro(status: number, corpo: object): Observable<never> {
  return throwError(() => new HttpErrorResponse({ status, error: corpo }));
}

describe('RedefinirSenha', () => {
  let auth: {
    esqueciSenha: ReturnType<typeof vi.fn<(email: string) => Observable<void>>>;
    conferirCodigoRedefinicao: ReturnType<
      typeof vi.fn<(email: string, codigo: string) => Observable<void>>
    >;
    redefinirSenha: ReturnType<
      typeof vi.fn<(email: string, codigo: string, novaSenha: string) => Observable<UsuarioResumo>>
    >;
  };
  let toast: { mostrar: ReturnType<typeof vi.fn<(mensagem: string) => void>> };
  let pendente: RedefinicaoPendente;

  beforeEach(() => {
    sessionStorage.clear();
    auth = { esqueciSenha: vi.fn(), conferirCodigoRedefinicao: vi.fn(), redefinirSenha: vi.fn() };
    toast = { mostrar: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });
    pendente = TestBed.inject(RedefinicaoPendente);
  });

  async function montar(): Promise<ComponentFixture<RedefinirSenha>> {
    const fixture = TestBed.createComponent(RedefinirSenha);
    await fixture.whenStable();
    return fixture;
  }

  function campo(fixture: ComponentFixture<RedefinirSenha>, id: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`#${id}`);
  }

  async function digitar(
    fixture: ComponentFixture<RedefinirSenha>,
    id: string,
    valor: string,
  ): Promise<void> {
    campo(fixture, id).value = valor;
    campo(fixture, id).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function enviar(fixture: ComponentFixture<RedefinirSenha>): Promise<void> {
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();
  }

  function texto(fixture: ComponentFixture<RedefinirSenha>): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  /** Monta a tela já no passo da senha, com o código 123456 conferido. */
  async function ateASenha(): Promise<ComponentFixture<RedefinirSenha>> {
    pendente.comecar('ana@exemplo.com');
    auth.conferirCodigoRedefinicao.mockReturnValue(of(undefined));
    const fixture = await montar();
    await digitar(fixture, 'redefinicao-codigo', '123456');
    return fixture;
  }

  it('pede o código pelo e-mail e passa para o código, guardando o pedido para um F5', async () => {
    auth.esqueciSenha.mockReturnValue(of(undefined));
    const fixture = await montar();
    expect(document.activeElement).toBe(campo(fixture, 'redefinicao-email'));

    await digitar(fixture, 'redefinicao-email', ' ana@exemplo.com ');
    await enviar(fixture);

    expect(auth.esqueciSenha).toHaveBeenCalledExactlyOnceWith('ana@exemplo.com');
    expect(texto(fixture)).toContain('Se houver uma conta com ana@exemplo.com');
    expect(texto(fixture)).toContain('peça outro código em 60 s');
    expect(pendente.pendente?.email).toBe('ana@exemplo.com');
  });

  it('não pede o código com um e-mail inválido', async () => {
    const fixture = await montar();

    await digitar(fixture, 'redefinicao-email', 'ana@');
    await enviar(fixture);

    expect(auth.esqueciSenha).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.field-error')?.textContent).toContain(
      'Informe um e-mail válido.',
    );
  });

  it('se um código acabou de ser mandado (429), vai para o código e espera o tempo do servidor', async () => {
    auth.esqueciSenha.mockReturnValue(
      erro(429, { detail: 'Espere 42 segundos para pedir outro código.', aguardarSegundos: 42 }),
    );
    const fixture = await montar();

    await digitar(fixture, 'redefinicao-email', 'ana@exemplo.com');
    await enviar(fixture);

    expect(campo(fixture, 'redefinicao-codigo')).not.toBeNull();
    expect(texto(fixture)).toContain('Espere 42 segundos para pedir outro código.');
    expect(texto(fixture)).toContain('peça outro código em 42 s');
  });

  it('depois de um F5, volta direto para o código', async () => {
    pendente.comecar('ana@exemplo.com');
    const fixture = await montar();

    expect(document.activeElement).toBe(campo(fixture, 'redefinicao-codigo'));
    expect(texto(fixture)).toContain('ana@exemplo.com');
  });

  it('confere o código sozinho no sexto número e mostra o erro da API embaixo do campo', async () => {
    pendente.comecar('ana@exemplo.com');
    auth.conferirCodigoRedefinicao.mockReturnValue(
      erro(400, { erros: { codigo: 'Código incorreto. Você tem mais 4 tentativas.' } }),
    );
    const fixture = await montar();

    await digitar(fixture, 'redefinicao-codigo', '000 000');

    expect(auth.conferirCodigoRedefinicao).toHaveBeenCalledExactlyOnceWith(
      'ana@exemplo.com',
      '000000',
    );
    expect(fixture.nativeElement.querySelector('.field-error')?.textContent).toContain(
      'Código incorreto. Você tem mais 4 tentativas.',
    );
    expect(campo(fixture, 'redefinicao-senha')).toBeNull();
  });

  it('com o código certo, pede a senha nova e entra na conta', async () => {
    auth.redefinirSenha.mockReturnValue(of(ANA));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await ateASenha();
    expect(document.activeElement).toBe(campo(fixture, 'redefinicao-senha'));

    await digitar(fixture, 'redefinicao-senha', 'outra-senha-forte');
    await digitar(fixture, 'redefinicao-confirmar', 'outra-senha-forte');
    await enviar(fixture);

    expect(auth.redefinirSenha).toHaveBeenCalledExactlyOnceWith(
      'ana@exemplo.com',
      '123456',
      'outra-senha-forte',
    );
    expect(toast.mostrar).toHaveBeenCalledWith('Senha nova salva. Boas-vindas de volta!');
    expect(navegar).toHaveBeenCalledWith('/');
    expect(pendente.pendente).toBeNull();
    expect(sessionStorage.getItem('campusconnect.redefinicao')).toBeNull();
  });

  it('não salva se a confirmação não bate com a senha nova', async () => {
    const fixture = await ateASenha();

    await digitar(fixture, 'redefinicao-senha', 'outra-senha-forte');
    await digitar(fixture, 'redefinicao-confirmar', 'outra-senha-fraca');
    await enviar(fixture);

    expect(auth.redefinirSenha).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('As senhas não são iguais.');
  });

  it('se o código venceu enquanto a pessoa criava a senha, volta para o código com o aviso', async () => {
    auth.redefinirSenha.mockReturnValue(
      erro(400, { erros: { codigo: 'Este código não vale mais. Peça um código novo.' } }),
    );
    const fixture = await ateASenha();

    await digitar(fixture, 'redefinicao-senha', 'outra-senha-forte');
    await digitar(fixture, 'redefinicao-confirmar', 'outra-senha-forte');
    await enviar(fixture);

    expect(campo(fixture, 'redefinicao-codigo').value).toBe('');
    expect(fixture.nativeElement.querySelector('.auth-alert')?.textContent).toContain(
      'Este código não vale mais. Peça um código novo.',
    );
  });

  it('"Usar outro e-mail" volta para o começo e esquece o pedido', async () => {
    pendente.comecar('ana@exemplo.com');
    const fixture = await montar();

    const botao = [...fixture.nativeElement.querySelectorAll('button')].find(
      (elemento: HTMLButtonElement) => elemento.textContent?.includes('Usar outro e-mail'),
    ) as HTMLButtonElement;
    botao.click();
    await fixture.whenStable();

    expect(campo(fixture, 'redefinicao-email').value).toBe('ana@exemplo.com');
    expect(pendente.pendente).toBeNull();
  });
});
