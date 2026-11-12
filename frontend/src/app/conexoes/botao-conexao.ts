import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Icone } from '../shared/icone';
import { ToastService } from '../shared/toast.service';
import {
  AcaoConexao,
  ConexaoService,
  SituacaoConexao,
  primeiroNome,
  recadoDaAcao,
} from './conexao.service';

/** O mínimo que o botão precisa saber de uma pessoa. */
export interface PessoaDoBotao {
  id: string;
  nomeCompleto: string;
}

/**
 * O botão de conexão com uma pessoa: "Conectar", "Aceitar" e "Recusar", "Pedido enviado" ou "Conectado". É o
 * botão da linha de pessoa do MVP ("Seguir" / "Seguindo"), trocado pelo modelo de conexões.
 *
 * Cancelar um pedido e desfazer uma conexão pedem confirmação ali mesmo, como a exclusão de bolha, porque o MVP não
 * tem modal. O botão some para a própria pessoa e enquanto as listas não carregaram: assim um F5 não oferece
 * "Conectar" a quem já é conexão.
 */
@Component({
  selector: 'app-botao-conexao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  styles: ':host { display: contents; }',
  template: `
    @if (visivel()) {
      @switch (situacao()) {
        @case ('NENHUMA') {
          <button
            type="button"
            class="btn btn-primary"
            [class.btn-sm]="compacto()"
            [disabled]="agindo() !== null"
            [attr.aria-label]="'Conectar com ' + pessoa().nomeCompleto"
            (click)="agir('conectar')"
          >
            {{ agindo() ? 'Enviando…' : 'Conectar' }}
          </button>
        }
        @case ('PEDIDO_RECEBIDO') {
          <span class="botoes-conexao">
            <button
              type="button"
              class="btn btn-primary"
              [class.btn-sm]="compacto()"
              [disabled]="agindo() !== null"
              [attr.aria-label]="'Aceitar o pedido de ' + pessoa().nomeCompleto"
              (click)="agir('conectar')"
            >
              {{ agindo() === 'conectar' ? 'Enviando…' : 'Aceitar' }}
            </button>
            <button
              type="button"
              class="btn btn-secondary"
              [class.btn-sm]="compacto()"
              [disabled]="agindo() !== null"
              [attr.aria-label]="'Recusar o pedido de ' + pessoa().nomeCompleto"
              (click)="agir('recusar')"
            >
              {{ agindo() === 'recusar' ? 'Enviando…' : 'Recusar' }}
            </button>
          </span>
        }
        @default {
          @if (confirmando()) {
            <span class="botoes-conexao" role="group" [attr.aria-label]="pergunta()">
              <button
                #confirmar
                type="button"
                class="btn btn-danger"
                [class.btn-sm]="compacto()"
                [disabled]="agindo() !== null"
                (click)="agir(desfazer())"
              >
                {{ agindo() ? 'Enviando…' : rotuloDeDesfazer() }}
              </button>
              <button
                type="button"
                class="btn btn-ghost"
                [class.btn-sm]="compacto()"
                [disabled]="agindo() !== null"
                (click)="confirmando.set(false)"
              >
                Manter
              </button>
            </span>
          } @else {
            <button
              type="button"
              class="btn btn-secondary"
              [class.btn-sm]="compacto()"
              [title]="rotuloDeDesfazer()"
              [attr.aria-label]="rotuloDoEstado()"
              (click)="abrirConfirmacao()"
            >
              @if (situacao() === 'CONECTADOS') {
                <app-icone nome="check" /> Conectado
              } @else {
                Pedido enviado
              }
            </button>
          }
        }
      }
    }
  `,
})
export class BotaoConexao {
  private readonly auth = inject(AuthService);
  private readonly conexoes = inject(ConexaoService);
  private readonly toast = inject(ToastService);
  private readonly injector = inject(Injector);

  readonly pessoa = input.required<PessoaDoBotao>();
  /** Nas listas de pessoas o botão é menor (`btn-sm`), como na linha de pessoa do MVP. */
  readonly compacto = input(false, { transform: booleanAttribute });
  /** Avisa a tela depois de cada ação, com a situação que o servidor devolveu. */
  readonly mudou = output<SituacaoConexao>();

  protected readonly visivel = computed(
    () => this.conexoes.situacoes() !== null && this.pessoa().id !== this.auth.usuario()?.id,
  );

  /** Vem das listas. A resposta de uma ação troca na hora, e a recarga que vem depois confirma. */
  protected readonly situacao = linkedSignal<SituacaoConexao>(
    () => this.conexoes.situacoes()?.get(this.pessoa().id) ?? 'NENHUMA',
  );

  /** Fecha sozinha quando a situação muda, por exemplo depois de cancelar. */
  protected readonly confirmando = linkedSignal({ source: this.situacao, computation: () => false });
  /** A ação em andamento, para o botão certo mostrar "Enviando…". */
  protected readonly agindo = signal<AcaoConexao | null>(null);

  protected readonly desfazer = computed<AcaoConexao>(() =>
    this.situacao() === 'CONECTADOS' ? 'desfazer' : 'cancelarPedido',
  );
  protected readonly rotuloDeDesfazer = computed(() =>
    this.situacao() === 'CONECTADOS' ? 'Desfazer conexão' : 'Cancelar pedido',
  );
  protected readonly rotuloDoEstado = computed(() =>
    this.situacao() === 'CONECTADOS'
      ? `Conectado com ${this.pessoa().nomeCompleto}. Desfazer conexão`
      : `Pedido enviado para ${this.pessoa().nomeCompleto}. Cancelar pedido`,
  );
  protected readonly pergunta = computed(() =>
    this.situacao() === 'CONECTADOS'
      ? `Desfazer a conexão com ${this.pessoa().nomeCompleto}?`
      : `Cancelar o pedido para ${this.pessoa().nomeCompleto}?`,
  );

  private readonly botaoConfirmar = viewChild<ElementRef<HTMLButtonElement>>('confirmar');
  private destruido = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destruido = true));
  }

  protected abrirConfirmacao(): void {
    this.confirmando.set(true);
    afterNextRender(() => this.botaoConfirmar()?.nativeElement.focus(), { injector: this.injector });
  }

  /**
   * A ação vai até o fim mesmo se a tela fechar no meio: o aviso aparece e as listas recarregam. Só a atualização
   * do próprio botão é pulada.
   */
  protected agir(acao: AcaoConexao): void {
    const pessoa = this.pessoa();
    this.agindo.set(acao);
    this.requisicao(acao, pessoa.id).subscribe({
      next: (situacao) => {
        this.toast.mostrar(recadoDaAcao(acao, situacao, primeiroNome(pessoa.nomeCompleto)));
        if (!this.destruido) {
          this.agindo.set(null);
          this.situacao.set(situacao);
          this.mudou.emit(situacao);
        }
      },
      error: (erro: unknown) => {
        this.toast.mostrar(
          erro instanceof HttpErrorResponse && erro.status === 404
            ? 'Essa conta não está mais disponível.'
            : 'Não foi possível concluir. Tente de novo.',
        );
        if (!this.destruido) {
          this.agindo.set(null);
        }
      },
    });
  }

  private requisicao(acao: AcaoConexao, usuarioId: string): Observable<SituacaoConexao> {
    switch (acao) {
      case 'conectar':
        return this.conexoes.conectar(usuarioId);
      case 'recusar':
        return this.conexoes.recusar(usuarioId);
      case 'cancelarPedido':
        return this.conexoes.cancelarPedido(usuarioId);
      case 'desfazer':
        return this.conexoes.desfazer(usuarioId);
    }
  }
}
