import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { Sidebar } from '../sidebar/sidebar';
import { Topbar } from '../topbar/topbar';

/**
 * A moldura das telas logadas, igual ao `App` do MVP: menu lateral, barra do topo e o conteúdo. O painel
 * da direita (`RightRail`) do MVP só aparece no feed, no explorar e no marketplace, que ainda não existem.
 *
 * Cada rota informa a trilha da barra do topo em `data.trilha`.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Sidebar, Topbar],
  styles: ':host { display: contents; }',
  template: `
    <div class="app no-rail" data-theme="light" data-density="comfortable">
      <app-sidebar />
      <div class="main">
        <app-topbar [trilha]="trilha()" />
        <router-outlet />
      </div>
    </div>
  `,
})
export class Shell {
  private readonly rota = inject(ActivatedRoute);

  /**
   * Lida só no fim de cada navegação: a moldura é criada no meio da primeira, quando a rota filha ainda não
   * tem dados.
   */
  protected readonly trilha = toSignal(
    inject(Router).events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.trilhaDaRotaAtual()),
    ),
    { initialValue: ['Início'] },
  );

  private trilhaDaRotaAtual(): string[] {
    let atual: ActivatedRoute | null = this.rota;
    while (atual?.firstChild) {
      atual = atual.firstChild;
    }
    return (atual?.snapshot?.data?.['trilha'] as string[] | undefined) ?? ['Início'];
  }
}
