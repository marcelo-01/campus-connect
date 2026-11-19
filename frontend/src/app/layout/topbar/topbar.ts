import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { NotificacaoService } from '../../notificacoes/notificacao.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { MenuAparencia } from '../aparencia/menu-aparencia';

/**
 * O `Topbar` do MVP. No MVP a trilha mostrava a hierarquia da bolha ativa; aqui ela faz isso na página de
 * uma bolha e, nas outras telas, mostra a tela atual. O sino leva às notificações, com o ponto do MVP quando há
 * alguma nova; o número fica no item do menu. O botão de aparência (tema e cor de destaque) não existia no MVP.
 *
 * A trilha pode quebrar em mais linhas e deixar a barra mais alta. Como o aviso de posts novos do feed
 * fica preso logo abaixo dela, a altura da barra vai para o CSS em `--altura-topbar`.
 *
 * Nas telas estreitas aparece à esquerda o botão que abre a gaveta do menu, a trilha fica só com o último nome
 * e a busca some (ela ainda não funciona).
 */
@Component({
  selector: 'app-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone, MenuAparencia],
  styles: ':host { display: contents; }',
  template: `
    <div class="topbar" #barra>
      <button
        type="button"
        class="icon-btn menu-botao"
        aria-label="Abrir menu"
        aria-controls="menu-lateral"
        [attr.aria-expanded]="menuAberto()"
        #botaoMenu
        (click)="abrirMenu.emit()"
      >
        <app-icone nome="menu" />
      </button>
      <div class="crumbs">
        @for (parte of trilha(); track $index; let ultima = $last) {
          @if (ultima) {
            <strong>{{ parte }}</strong>
          } @else {
            <span>{{ parte }}</span><span class="sep">›</span>
          }
        }
      </div>
      <div class="search">
        <app-icone nome="busca" />
        <input placeholder="Buscar pessoas, bolhas, posts…" (keydown.enter)="emBreve('Busca')" />
        <span class="search-atalho">⌘K</span>
      </div>
      <app-menu-aparencia />
      <a class="icon-btn" routerLink="/notificacoes" title="Notificações" [attr.aria-label]="rotuloDoSino()">
        <app-icone nome="notificacoes" />
        @if (notificacoes.naoLidas()) {
          <span class="dot"></span>
        }
      </a>
      <a class="topbar-avatar" routerLink="/perfil" title="Meu perfil">
        <app-avatar [nome]="auth.usuario()?.nomeCompleto" [fotoUrl]="auth.usuario()?.fotoUrl" tamanho="md" />
      </a>
    </div>
  `,
})
export class Topbar {
  protected readonly auth = inject(AuthService);
  protected readonly notificacoes = inject(NotificacaoService);
  private readonly toast = inject(ToastService);

  readonly trilha = input<string[]>(['Início']);
  /** Se a gaveta do menu está aberta, para o `aria-expanded` do botão de menu. */
  readonly menuAberto = input(false);
  readonly abrirMenu = output();

  protected readonly rotuloDoSino = computed(() => {
    const total = this.notificacoes.naoLidas();
    return total ? `Notificações, ${total} ${total === 1 ? 'nova' : 'novas'}` : 'Notificações';
  });

  private readonly barra = viewChild<ElementRef<HTMLElement>>('barra');
  private readonly botaoMenu = viewChild.required<ElementRef<HTMLButtonElement>>('botaoMenu');

  constructor() {
    const raiz = inject(DOCUMENT).documentElement;
    effect((aoLimpar) => {
      const barra = this.barra()?.nativeElement;
      if (!barra || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observador = new ResizeObserver(() =>
        raiz.style.setProperty('--altura-topbar', `${barra.getBoundingClientRect().height}px`),
      );
      observador.observe(barra);
      aoLimpar(() => {
        observador.disconnect();
        raiz.style.removeProperty('--altura-topbar');
      });
    });
  }

  /** Devolve o foco ao botão de menu quando a gaveta fecha com o foco dentro dela. */
  focarBotaoMenu(): void {
    this.botaoMenu().nativeElement.focus();
  }

  protected emBreve(recurso: string): void {
    this.toast.mostrar(`${recurso} — em breve`);
  }
}
