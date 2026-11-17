import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Icone } from '../shared/icone';
import { ToastService } from '../shared/toast.service';
import { MensagemService } from './mensagem.service';

/** O que a página de Mensagens lê do estado da navegação. */
export interface EstadoAberturaConversa {
  /** Um texto para o campo de envio já vir preenchido, como o do "Conversar com" do anúncio. Não é enviado sozinho. */
  rascunho?: string;
}

/**
 * Abre a conversa com uma pessoa: a que já existe ou uma nova, vazia. É o "Mensagem" do perfil e o "Conversar com"
 * do anúncio.
 */
@Component({
  selector: 'app-botao-mensagem',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  styles: ':host { display: contents; }',
  template: `
    <button type="button" [class]="classe()" [disabled]="abrindo()" (click)="abrir()">
      <app-icone nome="mensagens" /> {{ abrindo() ? 'Abrindo…' : rotulo() }}
    </button>
  `,
})
export class BotaoMensagem {
  private readonly mensagens = inject(MensagemService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly usuarioId = input.required<string>();
  readonly rotulo = input('Mensagem');
  readonly classe = input('btn btn-secondary');
  readonly rascunho = input<string | undefined>(undefined);

  protected readonly abrindo = signal(false);
  private destruido = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destruido = true));
  }

  protected abrir(): void {
    this.abrindo.set(true);
    this.mensagens.abrirCom(this.usuarioId()).subscribe({
      next: (conversa) => {
        const estado: EstadoAberturaConversa = { rascunho: this.rascunho() };
        void this.router.navigate(['/mensagens', conversa.id], { state: estado });
      },
      error: (erro: unknown) => {
        const detalhe = erro instanceof HttpErrorResponse ? (erro.error?.detail as string | undefined) : undefined;
        this.toast.mostrar(detalhe ?? 'Não foi possível abrir a conversa. Tente de novo.');
        if (!this.destruido) {
          this.abrindo.set(false);
        }
      },
    });
  }
}
