import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IconeOlho } from '../../shared/icones';
import {
  aplicarErroDaApi,
  emailValido,
  igualAoCampo,
  mensagemDeErro,
} from '../../shared/formulario';
import { ToastService } from '../../shared/toast.service';
import { AuthLayout } from '../auth-layout/auth-layout';
import { AuthService } from '../auth.service';
import {
  ContagemReenvio,
  INTERVALO_REENVIO_MS,
  MENSAGENS_CODIGO,
  prepararCampoCodigo,
} from '../codigo-por-email';
import { ProblemaApi } from '../sessao';
import { RedefinicaoPendente } from './redefinicao-pendente';

type Etapa = 'email' | 'codigo' | 'senha';

const TAMANHO_SENHA = 'A senha precisa ter de 8 a 128 caracteres.';

/** Mesmas mensagens do `EmailRequest` e do `RedefinicaoSenhaRequest` no backend. */
const MENSAGENS: Record<
  'email' | 'codigo' | 'novaSenha' | 'confirmarSenha',
  Record<string, string>
> = {
  email: {
    required: 'Informe seu e-mail.',
    email: 'Informe um e-mail válido.',
    maxlength: 'Use no máximo 150 caracteres.',
  },
  codigo: MENSAGENS_CODIGO,
  novaSenha: {
    required: 'Crie uma senha nova.',
    minlength: TAMANHO_SENHA,
    maxlength: TAMANHO_SENHA,
  },
  confirmarSenha: { required: 'Repita a senha nova.', diferente: 'As senhas não são iguais.' },
};

/**
 * O "Esqueceu a senha?" do login, em três passos na mesma tela: o e-mail, o código de 6 números mandado para ele e a
 * senha nova. O código é conferido antes da senha, para a pessoa saber logo se errou. A senha nova já entra na conta.
 *
 * O e-mail que espera o código fica no {@link RedefinicaoPendente}: depois de um F5, a tela volta direto para o
 * código. O código digitado não é guardado, então um F5 no passo da senha pede o código de novo.
 */
@Component({
  selector: 'app-redefinir-senha',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuthLayout, ReactiveFormsModule, RouterLink, IconeOlho],
  styles: ':host { display: contents; }',
  templateUrl: './redefinir-senha.html',
})
export class RedefinirSenha {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly redefinicao = inject(RedefinicaoPendente);
  private readonly injector = inject(Injector);

  protected readonly etapa = signal<Etapa>(this.redefinicao.pendente ? 'codigo' : 'email');
  protected readonly email = signal(this.redefinicao.pendente?.email ?? '');
  protected readonly enviando = signal(false);
  protected readonly reenviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  protected readonly mostrarSenha = signal(false);
  protected readonly mostrarConfirmacao = signal(false);
  protected readonly reenvio = new ContagemReenvio(this.redefinicao.pendente?.liberaReenvioEm ?? 0);

  /** O primeiro campo do passo na tela. */
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly formEmail = this.fb.group({
    // O login passa o e-mail que já estava digitado.
    email: [
      emailDoLogin(this.router),
      [Validators.required, emailValido, Validators.maxLength(150)],
    ],
  });
  protected readonly formCodigo = this.fb.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  protected readonly formSenha = this.fb.group({
    novaSenha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirmarSenha: ['', [Validators.required, igualAoCampo('novaSenha')]],
  });

  constructor() {
    prepararCampoCodigo(this.formCodigo.controls.codigo, () => {
      if (!this.enviando()) {
        this.conferirCodigo();
      }
    });
    // Mudar a senha pode fazer a confirmação passar a bater (ou deixar de bater).
    this.formSenha.controls.novaSenha.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() =>
        this.formSenha.controls.confirmarSenha.updateValueAndValidity({ emitEvent: false }),
      );

    afterNextRender(() => this.focar());
  }

  protected pedirCodigo(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.formEmail.invalid || this.enviando()) {
      this.formEmail.markAllAsTouched();
      return;
    }

    const email = this.formEmail.controls.email.value.trim();
    this.enviando.set(true);
    this.auth.esqueciSenha(email).subscribe({
      next: () => {
        this.esperarCodigo(email, INTERVALO_REENVIO_MS);
        this.enviando.set(false);
        this.irPara('codigo');
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        const espera = segundosDeEspera(erro);
        if (espera !== null) {
          // Um código já foi mandado há pouco para esse e-mail, e ele continua valendo enquanto estiver no prazo.
          this.esperarCodigo(email, espera * 1000);
          this.irPara('codigo');
        }
        this.erroGeral.set(aplicarErroDaApi(this.formEmail, erro));
      },
    });
  }

  protected conferirCodigo(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    // O campo, e não o formulário: no envio automático, o `valueChanges` do campo chega antes de o formulário
    // recalcular a validade.
    if (this.formCodigo.controls.codigo.invalid || this.enviando()) {
      this.formCodigo.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.auth
      .conferirCodigoRedefinicao(this.email(), this.formCodigo.controls.codigo.value)
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.irPara('senha');
        },
        error: (erro: unknown) => {
          this.erroGeral.set(aplicarErroDaApi(this.formCodigo, erro));
          this.enviando.set(false);
          // Com o código selecionado, digitar de novo já troca o código errado.
          afterNextRender(() => this.focar(), { injector: this.injector });
        },
      });
  }

  protected reenviar(): void {
    if (this.reenvio.segundos() > 0 || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.erroGeral.set(null);
    this.auth.esqueciSenha(this.email()).subscribe({
      next: () => {
        this.esperarCodigo(this.email(), INTERVALO_REENVIO_MS);
        this.formCodigo.reset();
        this.enviado.set(false);
        this.reenviando.set(false);
        this.toast.mostrar('Mandamos um código novo.');
        this.focar();
      },
      error: (erro: unknown) => {
        const espera = segundosDeEspera(erro);
        if (espera !== null) {
          this.esperarCodigo(this.email(), espera * 1000);
        }
        this.erroGeral.set(aplicarErroDaApi(this.formCodigo, erro));
        this.reenviando.set(false);
      },
    });
  }

  protected salvarSenha(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.formSenha.invalid || this.enviando()) {
      this.formSenha.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    const codigo = this.formCodigo.controls.codigo.value;
    this.auth
      .redefinirSenha(this.email(), codigo, this.formSenha.controls.novaSenha.value)
      .subscribe({
        next: () => {
          this.redefinicao.terminar();
          this.toast.mostrar('Senha nova salva. Boas-vindas de volta!');
          void this.router.navigateByUrl('/');
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          const problema =
            erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
          const erroDoCodigo = problema?.erros?.['codigo'];
          if (erroDoCodigo) {
            // O código venceu enquanto a pessoa criava a senha. A senha digitada fica para a próxima tentativa. O aviso
            // vai no alerta, e não no campo: o campo volta para a tela agora e recalcularia a validade, apagando o erro.
            this.formCodigo.reset();
            this.irPara('codigo');
            this.erroGeral.set(erroDoCodigo);
            return;
          }
          this.erroGeral.set(aplicarErroDaApi(this.formSenha, erro));
        },
      });
  }

  /** Volta para o primeiro passo e esquece o pedido. */
  protected usarOutroEmail(): void {
    this.redefinicao.terminar();
    this.formEmail.controls.email.setValue(this.email());
    this.formCodigo.reset();
    this.formSenha.reset();
    this.irPara('email');
  }

  protected mostrarErro(controle: AbstractControl): boolean {
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: keyof typeof MENSAGENS): string | null {
    const controle =
      campo === 'email'
        ? this.formEmail.controls.email
        : campo === 'codigo'
          ? this.formCodigo.controls.codigo
          : this.formSenha.controls[campo];
    return mensagemDeErro(controle, MENSAGENS[campo]);
  }

  private esperarCodigo(email: string, ms: number): void {
    this.email.set(email);
    this.redefinicao.comecar(email);
    this.redefinicao.esperar(ms);
    this.reenvio.aguardar(ms);
  }

  private irPara(etapa: Etapa): void {
    this.etapa.set(etapa);
    this.enviado.set(false);
    this.erroGeral.set(null);
    afterNextRender(() => this.focar(), { injector: this.injector });
  }

  /** Foca o primeiro campo do passo. O do código fica selecionado, para o próximo dígito trocar o que estava lá. */
  private focar(): void {
    const campo = this.campo()?.nativeElement;
    campo?.focus();
    if (this.etapa() === 'codigo') {
      campo?.select();
    }
  }
}

/** O e-mail que o login passou pelo `state` da navegação, se for um texto. */
function emailDoLogin(router: Router): string {
  const email: unknown = router.currentNavigation()?.extras.state?.['email'];
  return typeof email === 'string' ? email.trim() : '';
}

/** No 429, quantos segundos faltam para poder pedir outro código. Fora dele, `null`. */
function segundosDeEspera(erro: unknown): number | null {
  if (!(erro instanceof HttpErrorResponse) || erro.status !== 429) {
    return null;
  }
  return (erro.error as ProblemaApi | null)?.aguardarSegundos ?? 60;
}
