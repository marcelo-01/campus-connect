import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ToastService } from '../../shared/toast.service';
import { AuthService } from '../auth.service';
import { UsuarioResumo } from '../sessao';
import { VerificacaoPendente } from './verificacao-pendente';
import { VerificarEmail } from './verificar-email';

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

describe('VerificarEmail', () => {
  let auth: {
    verificarEmail: ReturnType<
      typeof vi.fn<(email: string, codigo: string) => Observable<UsuarioResumo>>
    >;
    reenviarCodigo: ReturnType<typeof vi.fn<(email: string) => Observable<void>>>;
  };
  let toast: { mostrar: ReturnType<typeof vi.fn<(mensagem: string) => void>> };
  let pendente: VerificacaoPendente;

  beforeEach(() => {
    sessionStorage.clear();
    auth = { verificarEmail: vi.fn(), reenviarCodigo: vi.fn() };
    toast = { mostrar: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });
    pendente = TestBed.inject(VerificacaoPendente);
    pendente.comecar('ana@exemplo.com');
  });

  async function montar(): Promise<ComponentFixture<VerificarEmail>> {
    const fixture = TestBed.createComponent(VerificarEmail);
    await fixture.whenStable();
    return fixture;
  }

  function campo(fixture: ComponentFixture<VerificarEmail>): HTMLInputElement {
    return fixture.nativeElement.querySelector('#verificacao-codigo');
  }

  async function digitar(fixture: ComponentFixture<VerificarEmail>, texto: string): Promise<void> {
    campo(fixture).value = texto;
    campo(fixture).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function texto(fixture: ComponentFixture<VerificarEmail>): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  it('mostra o e-mail que espera o código e já foca o campo', async () => {
    const fixture = await montar();

    expect(texto(fixture)).toContain('mandamos para ana@exemplo.com');
    expect(document.activeElement).toBe(campo(fixture));
  });

  it('fica só com os números e confere sozinho quando chegam os 6, mesmo colados com espaço', async () => {
    auth.verificarEmail.mockReturnValue(of(ANA));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await montar();

    await digitar(fixture, '12a');
    expect(campo(fixture).value).toBe('12');
    expect(auth.verificarEmail).not.toHaveBeenCalled();

    await digitar(fixture, '123 456');

    expect(auth.verificarEmail).toHaveBeenCalledExactlyOnceWith('ana@exemplo.com', '123456');
    expect(navegar).toHaveBeenCalledWith('/');
    expect(pendente.pendente).toBeNull();
    expect(sessionStorage.getItem('campusconnect.verificacao')).toBeNull();
  });

  it('mostra embaixo do campo o erro do código vindo da API', async () => {
    auth.verificarEmail.mockReturnValue(
      erro(400, { erros: { codigo: 'Código incorreto. Você tem mais 4 tentativas.' } }),
    );
    const fixture = await montar();

    await digitar(fixture, '000000');

    expect(fixture.nativeElement.querySelector('.field-error')?.textContent).toContain(
      'Código incorreto. Você tem mais 4 tentativas.',
    );
    expect(pendente.pendente).not.toBeNull();
  });

  it('e-mail já confirmado em outra aba: avisa e esquece a verificação', async () => {
    auth.verificarEmail.mockReturnValue(
      erro(409, { detail: 'Este e-mail já foi confirmado. Entre com a sua senha.' }),
    );
    const fixture = await montar();

    await digitar(fixture, '123456');

    expect(fixture.nativeElement.querySelector('.auth-alert')?.textContent).toContain(
      'Entre com a sua senha.',
    );
    expect(pendente.pendente).toBeNull();
  });

  it('só oferece outro código depois da espera, e recomeça a contagem ao reenviar', async () => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    try {
      auth.reenviarCodigo.mockReturnValue(of(undefined));
      const fixture = await montar();
      expect(texto(fixture)).toContain('peça outro código em 60 s');
      expect(fixture.nativeElement.querySelector('.link-acao')).toBeNull();

      vi.advanceTimersByTime(60_000);
      await fixture.whenStable();
      const botao: HTMLButtonElement = fixture.nativeElement.querySelector('.link-acao');
      botao.click();
      await fixture.whenStable();

      expect(auth.reenviarCodigo).toHaveBeenCalledWith('ana@exemplo.com');
      expect(toast.mostrar).toHaveBeenCalledWith('Mandamos um código novo.');
      expect(texto(fixture)).toContain('peça outro código em 60 s');
    } finally {
      vi.useRealTimers();
    }
  });

  it('no limite por hora, espera o tempo que o servidor mandou', async () => {
    pendente.esperar(0);
    auth.reenviarCodigo.mockReturnValue(
      erro(429, {
        detail: 'Você pediu muitos códigos. Tente de novo em 50 minutos.',
        aguardarSegundos: 3000,
      }),
    );
    const fixture = await montar();

    (fixture.nativeElement.querySelector('.link-acao') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(texto(fixture)).toContain('Tente de novo em 50 minutos.');
    expect(texto(fixture)).toContain('peça outro código em 50 min');
  });
});

describe('VerificacaoPendente', () => {
  beforeEach(() => sessionStorage.clear());

  it('sobrevive a um F5 pelo sessionStorage da aba', () => {
    TestBed.inject(VerificacaoPendente).comecar('ana@exemplo.com');
    TestBed.resetTestingModule();

    expect(TestBed.inject(VerificacaoPendente).pendente?.email).toBe('ana@exemplo.com');
  });

  it('ignora o que estiver salvo num formato estranho', () => {
    sessionStorage.setItem('campusconnect.verificacao', '{"email": 42}');

    expect(TestBed.inject(VerificacaoPendente).pendente).toBeNull();
  });
});
