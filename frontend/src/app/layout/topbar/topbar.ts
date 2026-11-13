import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';

/**
 * O `Topbar` do MVP. No MVP a trilha mostrava a hierarquia da bolha ativa; aqui ela faz isso na página de
 * uma bolha e, nas outras telas, mostra a tela atual. O ponto de "notificação nova" saiu porque não há
 * notificações ainda.
 *
 * A trilha pode quebrar em mais linhas e deixar a barra mais alta. Como o aviso de posts novos do feed
 * fica preso logo abaixo dela, a altura da barra vai para o CSS em `--altura-topbar`.
 */
@Component({
  selector: 'app-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone],
  styles: ':host { display: contents; }',
  template: `
    <div class="topbar" #barra>
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
        <span class="muted" style="font-size: 11px; padding: 2px 6px; background: var(--surface-2); border-radius: 6px">⌘K</span>
      </div>
      <button type="button" class="icon-btn" title="Notificações" (click)="emBreve('Notificações')">
        <app-icone nome="notificacoes" />
      </button>
      <a class="topbar-avatar" routerLink="/perfil" title="Meu perfil">
        <app-avatar [nome]="auth.usuario()?.nomeCompleto" [fotoUrl]="auth.usuario()?.fotoUrl" tamanho="md" />
      </a>
    </div>
  `,
})
export class Topbar {
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly trilha = input<string[]>(['Início']);

  private readonly barra = viewChild<ElementRef<HTMLElement>>('barra');

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

  protected emBreve(recurso: string): void {
    this.toast.mostrar(`${recurso} — em breve`);
  }
}
