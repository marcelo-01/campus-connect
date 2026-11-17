import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { AoVivoService } from '../../shared/aovivo/aovivo.service';
import { Sidebar } from '../sidebar/sidebar';
import { Topbar } from '../topbar/topbar';
import { TrilhaService } from '../trilha.service';

/**
 * A moldura das telas logadas, igual ao `App` do MVP: menu lateral, barra do topo e o conteúdo. O painel
 * da direita (`RightRail`) do MVP não aparece: todas as telas usam o layout sem ele (`no-rail`).
 *
 * O tema não fica aqui: o `AparenciaService` o põe no `<html>`, para valer também na tela de login.
 *
 * Enquanto a moldura está na tela, a conexão ao vivo fica ligada para a conta logada.
 *
 * Cada rota informa a trilha da barra do topo em `data.trilha`; uma tela pode trocá-la pelo
 * {@link TrilhaService} depois de carregar.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Sidebar, Topbar],
  styles: ':host { display: contents; }',
  template: `
    <div class="app no-rail" data-density="comfortable">
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
  private readonly router = inject(Router);
  private readonly trilhas = inject(TrilhaService);

  /**
   * Lida só no fim de cada navegação: a moldura é criada no meio da primeira, quando a rota filha ainda não
   * tem dados.
   */
  private readonly trilhaDaRota = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.trilhaDaRotaAtual()),
    ),
    { initialValue: ['Início'] },
  );

  protected readonly trilha = computed(() => this.trilhas.atual() ?? this.trilhaDaRota());

  constructor() {
    const aoVivo = inject(AoVivoService);
    const auth = inject(AuthService);
    effect(() => {
      const usuarioId = auth.usuario()?.id ?? null;
      untracked(() => aoVivo.conectarComo(usuarioId));
    });
    inject(DestroyRef).onDestroy(() => aoVivo.conectarComo(null));

    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.trilhas.limpar());
  }

  private trilhaDaRotaAtual(): string[] {
    let atual: ActivatedRoute | null = this.rota;
    while (atual?.firstChild) {
      atual = atual.firstChild;
    }
    return (atual?.snapshot?.data?.['trilha'] as string[] | undefined) ?? ['Início'];
  }
}
