import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { AoVivoService } from '../../shared/aovivo/aovivo.service';
import { BarraInferior } from '../barra-inferior/barra-inferior';
import { Sidebar } from '../sidebar/sidebar';
import { Topbar } from '../topbar/topbar';
import { TrilhaService } from '../trilha.service';

/**
 * A moldura das telas logadas, igual ao `App` do MVP: menu lateral, barra do topo e o conteúdo. O painel
 * da direita (`RightRail`) do MVP não aparece: todas as telas usam o layout sem ele (`no-rail`).
 *
 * Em telas estreitas (celular e tablet em pé), o menu lateral vira uma gaveta, aberta pelo botão de menu da
 * barra do topo, e os atalhos principais vão para a barra de baixo. A gaveta fecha ao navegar, no Esc e no
 * toque no fundo escurecido. Quem decide se a tela é estreita é o CSS: aqui fica só se a gaveta está aberta.
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
  imports: [RouterOutlet, Sidebar, Topbar, BarraInferior],
  styles: ':host { display: contents; }',
  host: { '(document:keydown.escape)': 'fecharMenu()' },
  template: `
    <div class="app no-rail" data-density="comfortable" [class.menu-aberto]="menuAberto()">
      <app-sidebar (fechar)="fecharMenu()" />
      <div class="menu-fundo" aria-hidden="true" (click)="fecharMenu()"></div>
      <div class="main">
        <app-topbar [trilha]="trilha()" [menuAberto]="menuAberto()" (abrirMenu)="abrirMenu()" />
        <router-outlet />
      </div>
      <app-barra-inferior />
    </div>
  `,
})
export class Shell {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly trilhas = inject(TrilhaService);
  private readonly injector = inject(Injector);

  private readonly sidebar = viewChild.required(Sidebar);
  private readonly topbar = viewChild.required(Topbar);

  /** A gaveta do menu, nas telas estreitas. Nas largas o menu está sempre à mostra e isto não muda nada. */
  protected readonly menuAberto = signal(false);

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
      .subscribe(() => {
        this.trilhas.limpar();
        // O RouterLink começa a navegação ainda dentro do clique no link da gaveta: a gaveta fecha aqui.
        this.fecharMenu();
      });
  }

  protected abrirMenu(): void {
    this.menuAberto.set(true);
    // A gaveta fechada fica invisível (visibility: hidden) e não aceita foco; aberta, o foco vai para ela.
    afterNextRender(() => this.sidebar().focar(), { injector: this.injector });
  }

  /** Com o foco dentro da gaveta, ele volta para o botão que a abriu, em vez de se perder no `<body>`. */
  protected fecharMenu(): void {
    if (!this.menuAberto()) {
      return;
    }
    const focoNaGaveta = this.sidebar().contemFoco();
    this.menuAberto.set(false);
    if (focoNaGaveta) {
      this.topbar().focarBotaoMenu();
    }
  }

  private trilhaDaRotaAtual(): string[] {
    let atual: ActivatedRoute | null = this.rota;
    while (atual?.firstChild) {
      atual = atual.firstChild;
    }
    return (atual?.snapshot?.data?.['trilha'] as string[] | undefined) ?? ['Início'];
  }
}
