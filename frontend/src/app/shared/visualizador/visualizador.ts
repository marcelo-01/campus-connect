import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  effect,
  inject,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';
import { Icone } from '../icone';
import { VisualizadorService } from './visualizador.service';

/** Quanto o dedo precisa andar, em px, para o gesto valer. */
const DESLIZE_MINIMO = 50;

/**
 * As fotos em tela cheia, sobre um fundo escuro, nos dois temas. Fica uma vez só na raiz do app e mostra o que o
 * {@link VisualizadorService} mandar.
 *
 * É um `<dialog>` modal: o navegador prende o foco nele, deixa o resto da página inerte e devolve o foco a quem o
 * abriu. Fecha no ×, no Esc, no clique fora da foto, deslizando a foto para baixo e no botão voltar do celular, que
 * sem isso sairia da página: ao abrir, o visualizador põe uma entrada no histórico, com o mesmo endereço. Com mais de
 * uma foto, as setas da tela, as do teclado e o deslizar para o lado passam de uma para outra.
 */
@Component({
  selector: 'app-visualizador',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  styles: ':host { display: contents; }',
  host: { '(window:popstate)': 'aoVoltarNoHistorico()' },
  template: `
    <dialog
      #dialogo
      class="visualizador"
      [attr.aria-label]="servico.foto()?.descricao ?? 'Foto'"
      (cancel)="aoCancelar($event)"
      (close)="servico.fechar()"
      (click)="aoClicar($event)"
      (keydown)="aoTeclar($event)"
      (pointerdown)="aoTocar($event)"
      (pointerup)="aoSoltar($event)"
      (pointercancel)="toque = null"
    >
      @if (servico.exibicao(); as exibicao) {
        <div class="visualizador-topo">
          <span class="visualizador-contador" aria-live="polite">
            @if (exibicao.fotos.length > 1) {
              {{ exibicao.indice + 1 }} de {{ exibicao.fotos.length }}
            }
          </span>
          <button type="button" class="visualizador-botao" aria-label="Fechar" (click)="servico.fechar()">
            <app-icone nome="fechar" />
          </button>
        </div>
        @if (servico.foto(); as foto) {
          <img
            class="visualizador-foto"
            [class.redonda]="exibicao.redonda"
            [src]="foto.url"
            [alt]="foto.descricao"
            draggable="false"
          />
        }
        @if (exibicao.fotos.length > 1) {
          <button
            type="button"
            class="visualizador-botao visualizador-seta anterior"
            aria-label="Foto anterior"
            [disabled]="exibicao.indice === 0"
            (click)="servico.anterior()"
          >
            <app-icone nome="voltar" />
          </button>
          <button
            type="button"
            class="visualizador-botao visualizador-seta proxima"
            aria-label="Próxima foto"
            [disabled]="exibicao.indice === exibicao.fotos.length - 1"
            (click)="servico.proxima()"
          >
            <app-icone nome="avancar" />
          </button>
        }
      }
    </dialog>
  `,
})
export class Visualizador {
  protected readonly servico = inject(VisualizadorService);
  private readonly janela = inject(DOCUMENT).defaultView;
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  /** Se a entrada que o visualizador pôs no histórico ainda está lá, para tirá-la ao fechar pelo ×. */
  private comEntradaNoHistorico = false;
  /** Onde o dedo encostou, para o gesto de deslizar. */
  protected toque: { x: number; y: number } | null = null;
  /** O toque virou gesto: o clique que o navegador manda em seguida não fecha nada. */
  private ignorarClique = false;

  constructor() {
    effect(() => {
      const aberto = this.servico.exibicao() !== null;
      untracked(() => (aberto ? this.mostrar() : this.esconder()));
    });

    // As vizinhas já vêm baixando, para a troca de foto não piscar.
    effect(() => {
      const exibicao = this.servico.exibicao();
      if (exibicao && typeof Image !== 'undefined') {
        for (const indice of [exibicao.indice - 1, exibicao.indice + 1]) {
          const vizinha = exibicao.fotos[indice];
          if (vizinha) {
            new Image().src = vizinha.url;
          }
        }
      }
    });

    // Se outra tela abre por baixo (a sessão caiu, por exemplo), o visualizador não fica por cima dela.
    inject(Router)
      .events.pipe(
        filter((evento) => evento instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.comEntradaNoHistorico = false;
        this.servico.fechar();
      });
  }

  /** O Esc: o fechamento passa pelo serviço, como os outros. */
  protected aoCancelar(evento: Event): void {
    evento.preventDefault();
    this.servico.fechar();
  }

  /** Fora da foto e dos botões, o clique fecha. */
  protected aoClicar(evento: MouseEvent): void {
    if (this.ignorarClique) {
      this.ignorarClique = false;
      return;
    }
    const alvo = evento.target instanceof Element ? evento.target : null;
    if (!alvo?.closest('button, .visualizador-foto')) {
      this.servico.fechar();
    }
  }

  protected aoTeclar(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowLeft') {
      evento.preventDefault();
      this.servico.anterior();
    } else if (evento.key === 'ArrowRight') {
      evento.preventDefault();
      this.servico.proxima();
    }
  }

  protected aoTocar(evento: PointerEvent): void {
    // Com dois dedos é zoom, e não gesto.
    this.toque = evento.isPrimary && evento.pointerType !== 'mouse' ? { x: evento.clientX, y: evento.clientY } : null;
  }

  /** Para o lado, troca de foto; para baixo, fecha. */
  protected aoSoltar(evento: PointerEvent): void {
    const inicio = this.toque;
    this.toque = null;
    if (!inicio || !evento.isPrimary) {
      return;
    }
    const dx = evento.clientX - inicio.x;
    const dy = evento.clientY - inicio.y;
    if (Math.abs(dx) >= DESLIZE_MINIMO && Math.abs(dx) > Math.abs(dy) * 1.5) {
      this.ignorarClique = true;
      if (dx < 0) {
        this.servico.proxima();
      } else {
        this.servico.anterior();
      }
    } else if (dy >= DESLIZE_MINIMO * 2 && dy > Math.abs(dx) * 1.5) {
      this.ignorarClique = true;
      this.servico.fechar();
    }
  }

  /** O voltar do navegador tirou a entrada do visualizador: fecha sem voltar de novo. */
  protected aoVoltarNoHistorico(): void {
    if (this.comEntradaNoHistorico && !this.estadoDoHistorico()?.['visualizador']) {
      this.comEntradaNoHistorico = false;
      this.servico.fechar();
    }
  }

  private mostrar(): void {
    const dialogo = this.dialogo().nativeElement;
    if (dialogo.open) {
      return;
    }
    if (typeof dialogo.showModal === 'function') {
      dialogo.showModal();
    } else {
      dialogo.setAttribute('open', '');
    }
    if (this.janela?.history && !this.comEntradaNoHistorico) {
      this.janela.history.pushState({ ...this.estadoDoHistorico(), visualizador: true }, '');
      this.comEntradaNoHistorico = true;
    }
  }

  private esconder(): void {
    const dialogo = this.dialogo().nativeElement;
    if (dialogo.open) {
      if (typeof dialogo.close === 'function') {
        dialogo.close();
      } else {
        dialogo.removeAttribute('open');
      }
    }
    this.ignorarClique = false;
    this.toque = null;
    if (this.comEntradaNoHistorico) {
      this.comEntradaNoHistorico = false;
      this.janela?.history.back();
    }
  }

  private estadoDoHistorico(): Record<string, unknown> | null {
    const estado: unknown = this.janela?.history.state;
    return estado && typeof estado === 'object' ? (estado as Record<string, unknown>) : null;
  }
}
