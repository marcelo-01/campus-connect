import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { aplicarErroDaApi, mensagemDeErro } from '../../shared/formulario';
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
import { VerificacaoPendente } from './verificacao-pendente';

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
  protected readonly reenvio = new ContagemReenvio(this.verificacao.pendente?.liberaReenvioEm ?? 0);

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    // Com os 6 números, envia sozinho.
    prepararCampoCodigo(this.form.controls.codigo, () => {
      if (!this.enviando()) {
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
    if (this.reenvio.segundos() > 0 || this.reenviando()) {
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
    return mensagemDeErro(this.form.controls.codigo, MENSAGENS_CODIGO);
  }

  private aguardar(ms: number): void {
    this.verificacao.esperar(ms);
    this.reenvio.aguardar(ms);
  }
}
