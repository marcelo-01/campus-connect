import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { UsuarioResumo } from '../../auth/sessao';
import { ToastService } from '../../shared/toast.service';
import { AlterarSenha } from './alterar-senha';

const ANA: UsuarioResumo = {
  id: 'ana',
  username: 'ana',
  nomeCompleto: 'Ana Beatriz',
  nomeExibicao: null,
  email: 'ana@exemplo.com',
  fotoUrl: null,
  administrador: false,
};

function erro(status: number, corpo: object | null): Observable<never> {
  return throwError(() => new HttpErrorResponse({ status, error: corpo }));
}

describe('AlterarSenha', () => {
  let auth: {
    usuario: ReturnType<typeof signal<UsuarioResumo | null>>;
    alterarSenha: ReturnType<
      typeof vi.fn<(senhaAtual: string, novaSenha: string) => Observable<UsuarioResumo>>
    >;
  };
  let toast: { mostrar: ReturnType<typeof vi.fn<(mensagem: string) => void>> };
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth = { usuario: signal<UsuarioResumo | null>(ANA), alterarSenha: vi.fn() };
    toast = { mostrar: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  async function preencher(
    atual: string,
    nova: string,
    confirmacao = nova,
  ): Promise<ComponentFixture<AlterarSenha>> {
    const fixture = TestBed.createComponent(AlterarSenha);
    await fixture.whenStable();
    for (const [id, valor] of [
      ['senha-atual', atual],
      ['senha-nova', nova],
      ['senha-confirmar', confirmacao],
    ]) {
      const campo: HTMLInputElement = fixture.nativeElement.querySelector(`#${id}`);
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    }
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    return fixture;
  }

  function erroDoCampo(fixture: ComponentFixture<AlterarSenha>): string {
    return fixture.nativeElement.querySelector('.field-error')?.textContent ?? '';
  }

  it('troca a senha e volta para "Editar perfil"', async () => {
    auth.alterarSenha.mockReturnValue(of(ANA));

    await preencher('senha-forte-123', 'outra-senha-forte');

    expect(auth.alterarSenha).toHaveBeenCalledExactlyOnceWith(
      'senha-forte-123',
      'outra-senha-forte',
    );
    expect(toast.mostrar).toHaveBeenCalledWith(
      'Senha alterada. A conta saiu dos outros aparelhos.',
    );
    expect(navegar).toHaveBeenCalledWith('/perfil/editar');
  });

  it('não envia se a confirmação não bate ou a senha nova é curta', async () => {
    const diferente = await preencher('senha-forte-123', 'outra-senha-forte', 'outra-senha-fraca');
    expect(erroDoCampo(diferente)).toContain('As senhas não são iguais.');

    const curta = await preencher('senha-forte-123', 'curta');
    expect(erroDoCampo(curta)).toContain('A senha precisa ter de 8 a 128 caracteres.');

    expect(auth.alterarSenha).not.toHaveBeenCalled();
  });

  it('mostra embaixo do campo a senha atual errada', async () => {
    auth.alterarSenha.mockReturnValue(
      erro(400, { erros: { senhaAtual: 'A senha atual está incorreta.' } }),
    );

    const fixture = await preencher('errada', 'outra-senha-forte');

    expect(erroDoCampo(fixture)).toContain('A senha atual está incorreta.');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('com a sessão vencida (401), manda para o login', async () => {
    auth.alterarSenha.mockReturnValue(erro(401, null));

    await preencher('senha-forte-123', 'outra-senha-forte');

    expect(toast.mostrar).toHaveBeenCalledWith('Sua sessão expirou. Entre novamente.');
    expect(navegar).toHaveBeenCalledWith('/entrar');
  });
});
