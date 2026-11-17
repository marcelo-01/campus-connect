import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  booleanAttribute,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icone } from '../shared/icone';
import { ToastService } from '../shared/toast.service';
import { ConexaoService, SituacaoConexao, primeiroNome, recadoDaAcao } from './conexao.service';

/** O mínimo que o menu precisa saber de uma pessoa. */
export interface PessoaDoMenu {
  id: string;
  username: string;
  nomeCompleto: string;
}

/**
 * O menu "…" de uma pessoa, no perfil e no cabeçalho da conversa: "Ver perfil" (na conversa) e "Bloquear" ou
 * "Desbloquear". Bloquear pede confirmação ali mesmo, como a exclusão de uma publicação, porque o MVP não tem modal.
 * Desbloquear não pede: dá para bloquear de novo a qualquer momento.
 */
@Component({
  selector: 'app-menu-pessoa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icone],
  styles: ':host { display: contents; }',
  host: {
    '(document:click)': 'fecharSeForFora($event)',
    '(document:keydown.escape)': 'fechar()',
  },
  template: `
    <div class="menu-ancora">
      <button
        type="button"
        class="icon-btn"
        [attr.aria-label]="'Mais opções para ' + pessoa().nomeCompleto"
        aria-haspopup="menu"
        [attr.aria-expanded]="aberto()"
        (click)="alternar()"
      >
        <app-icone nome="opcoes" />
      </button>
      @if (aberto()) {
        @if (confirmando()) {
          <div class="menu-flutuante menu-confirmacao" role="group" [attr.aria-label]="'Bloquear ' + nome()">
            <p>
              <b>Bloquear {{ nome() }}?</b> Vocês não vão poder trocar mensagens nem pedir conexão, e a conexão ou o
              pedido entre vocês é desfeito. {{ nome() }} não recebe aviso.
            </p>
            <div class="hstack">
              <button type="button" class="btn btn-danger btn-sm" [disabled]="agindo()" (click)="bloquear()">
                {{ agindo() ? 'Bloqueando…' : 'Bloquear' }}
              </button>
              <button type="button" class="btn btn-ghost btn-sm" [disabled]="agindo()" (click)="confirmando.set(false)">
                Cancelar
              </button>
            </div>
          </div>
        } @else {
          <div class="menu-flutuante" role="menu">
            @if (comPerfil()) {
              <a role="menuitem" [routerLink]="['/perfil', pessoa().username]" (click)="fechar()">
                <app-icone nome="perfil" /> Ver perfil
              </a>
            }
            @if (bloqueada()) {
              <button type="button" role="menuitem" [disabled]="agindo()" (click)="desbloquear()">
                <app-icone nome="bloquear" /> Desbloquear {{ nome() }}
              </button>
            } @else if (carregou()) {
              <button type="button" role="menuitem" class="perigo" (click)="confirmando.set(true)">
                <app-icone nome="bloquear" /> Bloquear {{ nome() }}
              </button>
            }
          </div>
        }
      }
    </div>
  `,
})
export class MenuPessoa {
  private readonly conexoes = inject(ConexaoService);
  private readonly toast = inject(ToastService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly pessoa = input.required<PessoaDoMenu>();
  /** Mostra "Ver perfil": no cabeçalho da conversa, mas não no próprio perfil. */
  readonly comPerfil = input(false, { transform: booleanAttribute });
  /** Avisa a tela depois de bloquear ou desbloquear, com a situação que o servidor devolveu. */
  readonly mudou = output<SituacaoConexao>();

  protected readonly aberto = signal(false);
  protected readonly confirmando = signal(false);
  protected readonly agindo = signal(false);

  protected readonly nome = computed(() => primeiroNome(this.pessoa().nomeCompleto));
  protected readonly carregou = computed(() => this.conexoes.situacoes() !== null);
  protected readonly bloqueada = computed(() => this.conexoes.situacoes()?.get(this.pessoa().id) === 'BLOQUEADA');

  private destruido = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destruido = true));
  }

  protected alternar(): void {
    this.confirmando.set(false);
    this.aberto.update((aberto) => !aberto);
    if (this.aberto()) {
      // A situação pode ter mudado em outra aba.
      this.conexoes.recarregarMinhas();
    }
  }

  protected fechar(): void {
    this.aberto.set(false);
    this.confirmando.set(false);
  }

  protected fecharSeForFora(evento: Event): void {
    if (this.aberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.fechar();
    }
  }

  protected bloquear(): void {
    this.agir(this.conexoes.bloquear(this.pessoa().id), 'bloquear');
  }

  protected desbloquear(): void {
    this.agir(this.conexoes.desbloquear(this.pessoa().id), 'desbloquear');
  }

  private agir(
    requisicao: ReturnType<ConexaoService['bloquear']>,
    acao: 'bloquear' | 'desbloquear',
  ): void {
    const nome = this.nome();
    this.agindo.set(true);
    requisicao.subscribe({
      next: (situacao) => {
        this.toast.mostrar(recadoDaAcao(acao, situacao, nome));
        if (!this.destruido) {
          this.agindo.set(false);
          this.fechar();
          this.mudou.emit(situacao);
        }
      },
      error: () => {
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
        if (!this.destruido) {
          this.agindo.set(false);
        }
      },
    });
  }
}
