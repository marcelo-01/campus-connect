import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { aplicarErroDaApi, mensagemDeErro } from '../../shared/formulario';
import { ToastService } from '../../shared/toast.service';
import { AuthLayout } from '../auth-layout/auth-layout';
import { AuthService } from '../auth.service';
import { ProblemaApi } from '../sessao';
import { INTERVALO_REENVIO_MS, VerificacaoPendente } from './verificacao-pendente';

/** Mesmas mensagens do `VerificacaoEmailRequest` no backend. */
const MENSAGENS: Record<string, string> = {
  required: 'Digite o código que mandamos para o seu e-mail.',
  pattern: 'O código tem 6 números.',
};

/**
 * Tela do código de 6 números mandado para o e-mail, depois do cadastro ou de um login sem verificar. O código certo
 * já abre a sessão. O e-mail vem do {@link VerificacaoPendente}; sem ele, o `verificacaoPendenteGuard` manda de volta
 * para o cadastro.
 */
@Component({
  selector: 'app-verificar-email',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuthLayout, ReactiveFormsModule, RouterLink],
  styles: ':host { display: contents; }',
  templateUrl: './verificar-email.html',
})
export class VerificarEmail {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly verificacao = inject(VerificacaoPendente);
  private readonly injector = inject(Injector);

  protected readonly email = this.verificacao.pendente?.email ?? '';
  protected readonly enviando = signal(false);
  protected readonly reenviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);

  private readonly agora = signal(Date.now());
  private readonly liberaReenvioEm = signal(this.verificacao.pendente?.liberaReenvioEm ?? 0);
  /** Segundos até poder pedir outro código. */
  protected readonly espera = computed(() =>
    Math.max(0, Math.ceil((this.liberaReenvioEm() - this.agora()) / 1000)),
  );
  protected readonly tempoDeEspera = computed(() => {
    const segundos = this.espera();
    return segundos <= 60 ? `${segundos} s` : `${Math.ceil(segundos / 60)} min`;
  });

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    const relogio = setInterval(() => this.agora.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(relogio));

    // Só números, até 6. O código colado do e-mail pode vir com espaço ou hífen. Com os 6, envia sozinho.
    const codigo = this.form.controls.codigo;
    codigo.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      const digitos = valor.replace(/\D/g, '').slice(0, 6);
      if (digitos !== valor) {
        codigo.setValue(digitos);
      } else if (digitos.length === 6 && !this.enviando()) {
        this.verificar();
      }
    });

    afterNextRender(() => this.campo().nativeElement.focus());
  }

  protected verificar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    // O campo, e não o formulário: no envio automático, o `valueChanges` do campo chega antes de o formulário
    // recalcular a validade, e `form.invalid` ainda seria a de antes do último dígito.
    if (this.form.controls.codigo.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.auth.verificarEmail(this.email, this.form.controls.codigo.value).subscribe({
      next: () => {
        this.verificacao.terminar();
        this.toast.mostrar('E-mail confirmado. Boas-vindas!');
        void this.router.navigateByUrl('/');
      },
      error: (erro: unknown) => {
        if (erro instanceof HttpErrorResponse && erro.status === 409) {
          // Já foi verificado, talvez em outra aba. Agora a entrada é pela senha.
          this.verificacao.terminar();
        }
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
        this.enviando.set(false);
        // Com o código selecionado, digitar de novo já troca o código errado.
        afterNextRender(() => this.campo().nativeElement.select(), { injector: this.injector });
      },
    });
  }

  protected reenviar(): void {
    if (this.espera() > 0 || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.erroGeral.set(null);
    this.auth.reenviarCodigo(this.email).subscribe({
      next: () => {
        this.aguardar(INTERVALO_REENVIO_MS);
        this.form.reset();
        this.enviado.set(false);
        this.reenviando.set(false);
        this.toast.mostrar('Mandamos um código novo.');
        this.campo().nativeElement.focus();
      },
      error: (erro: unknown) => {
        if (erro instanceof HttpErrorResponse && erro.status === 429) {
          this.aguardar(((erro.error as ProblemaApi | null)?.aguardarSegundos ?? 60) * 1000);
        }
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
        this.reenviando.set(false);
      },
    });
  }

  protected mostrarErro(): boolean {
    const controle = this.form.controls.codigo;
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(): string | null {
    return mensagemDeErro(this.form.controls.codigo, MENSAGENS);
  }

  private aguardar(ms: number): void {
    this.verificacao.esperar(ms);
    this.agora.set(Date.now());
    this.liberaReenvioEm.set(Date.now() + ms);
  }
}
