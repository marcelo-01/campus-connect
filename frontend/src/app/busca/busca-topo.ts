import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  InjectionToken,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { Avatar } from '../shared/avatar';
import { Icone } from '../shared/icone';
import {
  AbaBusca,
  BuscaService,
  SUGESTOES_DE_PESSOAS,
  TAMANHO_BUSCA,
  abaDaUrl,
  enderecoDaBusca,
} from './busca.service';

/** Quanto a digitação precisa parar para as sugestões serem pedidas, em milissegundos. */
export const ESPERA_DAS_SUGESTOES = new InjectionToken<number>('ESPERA_DAS_SUGESTOES', {
  factory: () => 200,
});

/**
 * O campo de busca da barra do topo, que no MVP era só enfeite. Enquanto a pessoa digita, a lista embaixo dele sugere
 * até 5 pessoas, como no X; a primeira linha da lista, e o Enter, abrem a página de busca com o que foi digitado.
 *
 * É um combobox: o foco fica no campo, e as setas escolhem a linha da lista (`aria-activedescendant`). O Esc fecha a
 * lista, e Ctrl+K (⌘K no Mac) leva o foco ao campo de qualquer tela. Na página de busca, o campo mostra o termo da
 * URL; nas outras, ele começa vazio.
 *
 * Nas telas estreitas o CSS esconde o campo, e a barra do topo mostra uma lupa que leva à página de busca.
 */
@Component({
  selector: 'app-busca-topo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone],
  styles: ':host { display: contents; }',
  host: { '(document:keydown)': 'atalho($event)' },
  template: `
    <div class="search">
      <app-icone nome="busca" />
      <input
        #campo
        type="search"
        role="combobox"
        enterkeyhint="search"
        autocomplete="off"
        placeholder="Buscar pessoas, bolhas, posts…"
        aria-label="Buscar pessoas, bolhas e posts"
        aria-autocomplete="list"
        aria-controls="busca-sugestoes"
        [attr.maxlength]="tamanhoBusca"
        [attr.aria-expanded]="listaAberta()"
        [attr.aria-activedescendant]="ativa() === null ? null : 'busca-opcao-' + ativa()"
        [value]="texto()"
        (input)="digitar($event)"
        (focus)="aberta.set(true)"
        (blur)="fechar()"
        (keydown)="teclar($event)"
      />
      @if (!texto()) {
        <span class="search-atalho" aria-hidden="true">{{ rotuloDoAtalho }}</span>
      }
      @if (listaAberta()) {
        <!-- O mousedown não tira o foco do campo: assim o blur não fecha a lista antes do clique. -->
        <div
          class="menu-flutuante busca-sugestoes"
          id="busca-sugestoes"
          role="listbox"
          aria-label="Sugestões de busca"
          (mousedown)="$event.preventDefault()"
        >
          <a
            id="busca-opcao-0"
            role="option"
            class="busca-opcao"
            [class.ativa]="ativa() === 0"
            [attr.aria-selected]="ativa() === 0"
            [routerLink]="paginaDaBusca().caminho"
            [queryParams]="paginaDaBusca().queryParams"
            (click)="escolheu()"
          >
            <span class="busca-opcao-icone"><app-icone nome="busca" /></span>
            <span class="busca-opcao-texto">
              <span class="busca-opcao-nome">Buscar “{{ termo() }}”</span>
            </span>
          </a>
          @for (pessoa of sugestoes(); track pessoa.id; let i = $index) {
            <a
              role="option"
              class="busca-opcao"
              [id]="'busca-opcao-' + (i + 1)"
              [class.ativa]="ativa() === i + 1"
              [attr.aria-selected]="ativa() === i + 1"
              [routerLink]="['/perfil', pessoa.username]"
              (click)="escolheu()"
            >
              <app-avatar [nome]="pessoa.nomeCompleto" [fotoUrl]="pessoa.fotoUrl" tamanho="md" />
              <span class="busca-opcao-texto">
                <span class="busca-opcao-nome">{{ pessoa.nomeCompleto }}</span>
                <span class="busca-opcao-detalhe">&#64;{{ pessoa.username }}</span>
              </span>
            </a>
          }
        </div>
      }
    </div>
  `,
})
export class BuscaTopo {
  private readonly router = inject(Router);
  private readonly busca = inject(BuscaService);
  private readonly janela = inject(DOCUMENT).defaultView;

  protected readonly tamanhoBusca = TAMANHO_BUSCA;
  protected readonly rotuloDoAtalho = ehMac(this.janela?.navigator) ? '⌘K' : 'Ctrl K';

  protected readonly texto = signal('');
  protected readonly termo = computed(() => this.texto().trim());
  /** Com o foco no campo, a lista aparece sempre que há algo digitado. O Esc fecha até a próxima tecla. */
  protected readonly aberta = signal(false);
  protected readonly listaAberta = computed(() => this.aberta() && this.termo() !== '');
  protected readonly sugestoes = signal<PessoaPublica[]>([]);
  /** A linha escolhida pelas setas: 0 é "Buscar", e as pessoas vêm depois. `null` é nenhuma. */
  protected readonly ativa = signal<number | null>(null);

  /** A aba aberta na página de busca, para uma busca nova continuar nela. */
  private readonly abaAtual = signal<AbaBusca>('posts');
  protected readonly paginaDaBusca = computed(() => enderecoDaBusca(this.termo(), this.abaAtual()));

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');

  constructor() {
    // As sugestões seguem o termo; a resposta de um termo antigo é descartada pelo switchMap.
    toObservable(this.termo)
      .pipe(
        debounceTime(inject(ESPERA_DAS_SUGESTOES)),
        distinctUntilChanged(),
        switchMap((termo) =>
          termo
            ? this.busca.pessoas(termo, null, SUGESTOES_DE_PESSOAS).pipe(
                map((pagina) => pagina.itens),
                catchError(() => of([])),
              )
            : of([]),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((pessoas) => {
        this.sugestoes.set(pessoas);
        this.ativa.set(null);
      });

    // Na página de busca, o campo mostra o termo da URL; ao sair dela, volta vazio.
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const url = this.router.parseUrl(this.router.url);
        const naBusca = url.root.children['primary']?.segments[0]?.path === 'busca';
        this.texto.set(naBusca ? (url.queryParams['q'] ?? '') : '');
        this.abaAtual.set(naBusca ? abaDaUrl(url.queryParams['aba'] ?? null) : 'posts');
        this.aberta.set(false);
        this.ativa.set(null);
      });
  }

  protected digitar(evento: Event): void {
    this.texto.set((evento.target as HTMLInputElement).value);
    this.aberta.set(true);
    this.ativa.set(null);
  }

  protected teclar(evento: KeyboardEvent): void {
    const total = 1 + this.sugestoes().length;
    switch (evento.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        if (!this.termo()) {
          return;
        }
        evento.preventDefault();
        if (!this.listaAberta()) {
          this.aberta.set(true);
          return;
        }
        const atual = this.ativa();
        const passo = evento.key === 'ArrowDown' ? 1 : -1;
        this.ativa.set(atual === null ? (passo === 1 ? 0 : total - 1) : (atual + passo + total) % total);
        return;
      }
      case 'Enter':
        evento.preventDefault();
        this.abrir(this.listaAberta() ? (this.ativa() ?? 0) : 0);
        return;
      case 'Escape':
        if (this.listaAberta()) {
          this.aberta.set(false);
          this.ativa.set(null);
        } else {
          this.campo().nativeElement.blur();
        }
        return;
    }
  }

  /** Ctrl+K ou ⌘K. Nas telas estreitas o campo está escondido, e o atalho abre a página de busca. */
  protected atalho(evento: KeyboardEvent): void {
    if (evento.key.toLowerCase() !== 'k' || !(evento.ctrlKey || evento.metaKey) || evento.altKey || evento.shiftKey) {
      return;
    }
    evento.preventDefault();
    const campo = this.campo().nativeElement;
    const caixa = campo.closest('.search');
    if (caixa && this.janela?.getComputedStyle(caixa).display === 'none') {
      void this.router.navigate(['/busca']);
      return;
    }
    campo.focus();
    campo.select();
  }

  protected fechar(): void {
    this.aberta.set(false);
    this.ativa.set(null);
  }

  /** Depois de um clique numa linha, o RouterLink navega; aqui só a lista fecha e o campo solta o foco. */
  protected escolheu(): void {
    this.fechar();
    this.campo().nativeElement.blur();
  }

  /** A linha escolhida pelo teclado: 0 abre a página de busca; as outras, o perfil da pessoa. */
  private abrir(linha: number): void {
    if (!this.termo()) {
      return;
    }
    const pessoa = linha > 0 ? this.sugestoes()[linha - 1] : undefined;
    if (pessoa) {
      void this.router.navigate(['/perfil', pessoa.username]);
    } else {
      const { caminho, queryParams } = this.paginaDaBusca();
      void this.router.navigate(caminho, { queryParams });
    }
    this.escolheu();
  }
}

/** O atalho aparece como ⌘K no Mac, no iPhone e no iPad, e como Ctrl K nos outros. */
export function ehMac(navegador: Navigator | undefined): boolean {
  return /Mac|iPhone|iPad/.test(navegador?.platform || navegador?.userAgent || '');
}
