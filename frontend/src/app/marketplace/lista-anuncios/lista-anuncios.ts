import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, tap } from 'rxjs';
import { ToastService } from '../../shared/toast.service';
import { AnuncioCartao } from '../anuncio-cartao/anuncio-cartao';
import { AnuncioCard, AnuncioService, FonteAnuncios } from '../anuncio.service';

/**
 * A grade de anúncios do `MarketScreen` do MVP (`.market-grid`), usada na vitrine e na aba Marketplace do
 * perfil. Carrega mais ao chegar perto do fim da página, como o feed, e pelo botão "Ver mais", para quem
 * navega pelo teclado.
 *
 * O estado vazio fica com quem usa a lista, no conteúdo com o atributo `vazio`.
 */
@Component({
  selector: 'app-lista-anuncios',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AnuncioCartao],
  styles: ':host { display: contents; }',
  templateUrl: './lista-anuncios.html',
})
export class ListaAnuncios {
  private readonly servico = inject(AnuncioService);
  private readonly toast = inject(ToastService);

  readonly fonte = input.required<FonteAnuncios>();
  /** Quantos anúncios passam no filtro da vitrine, quando a primeira página chega. */
  readonly total = output<number | null>();

  protected readonly itens = signal<AnuncioCard[]>([]);
  protected readonly proximo = signal<string | null>(null);
  protected readonly carregando = signal(true);
  protected readonly carregandoMais = signal(false);
  protected readonly falhou = signal(false);

  /** Muda a cada "Tentar de novo", para o pedido sair outra vez. */
  private readonly tentativa = signal(0);
  /** Muda a cada recarga: uma página que chega atrasada, de antes da troca de filtro, é descartada. */
  private geracao = 0;
  private readonly fim = viewChild<ElementRef<HTMLElement>>('fim');

  constructor() {
    const pedido = computed(() => ({ fonte: this.fonte(), tentativa: this.tentativa() }), {
      equal: (a, b) => JSON.stringify(a) === JSON.stringify(b),
    });
    toObservable(pedido)
      .pipe(
        tap(() => {
          this.geracao++;
          this.carregando.set(true);
          this.carregandoMais.set(false);
          this.falhou.set(false);
          this.itens.set([]);
          this.proximo.set(null);
        }),
        switchMap(({ fonte }) =>
          this.servico.pagina(fonte, null).pipe(
            catchError(() => {
              this.falhou.set(true);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        this.carregando.set(false);
        if (pagina) {
          this.itens.set(pagina.itens);
          this.proximo.set(pagina.proximo);
          this.total.emit(pagina.total);
        }
      });

    // Rolagem infinita: o marcador do fim da lista chegou perto da tela.
    effect((aoLimpar) => {
      const fim = this.fim()?.nativeElement;
      if (!fim || typeof IntersectionObserver === 'undefined') {
        return;
      }
      const observador = new IntersectionObserver(
        (entradas) => {
          if (entradas.some((entrada) => entrada.isIntersecting)) {
            this.carregarMais();
          }
        },
        { rootMargin: '600px 0px' },
      );
      observador.observe(fim);
      aoLimpar(() => observador.disconnect());
    });
  }

  protected carregarMais(): void {
    const cursor = this.proximo();
    if (!cursor || this.carregandoMais() || this.carregando()) {
      return;
    }
    const geracao = this.geracao;
    this.carregandoMais.set(true);
    this.servico.pagina(this.fonte(), cursor).subscribe({
      next: (pagina) => {
        if (geracao !== this.geracao) {
          return;
        }
        this.itens.update((atuais) => {
          const vistos = new Set(atuais.map((anuncio) => anuncio.id));
          return [...atuais, ...pagina.itens.filter((anuncio) => !vistos.has(anuncio.id))];
        });
        this.proximo.set(pagina.proximo);
        this.carregandoMais.set(false);
      },
      error: () => {
        if (geracao === this.geracao) {
          this.carregandoMais.set(false);
          this.toast.mostrar('Não foi possível carregar mais. Tente de novo.');
        }
      },
    });
  }

  protected tentarDeNovo(): void {
    this.tentativa.update((n) => n + 1);
  }

  /** O card mudou: o mesmo anúncio fica em dia na lista. Nos favoritos, o desfavoritado sai. */
  protected atualizar(anuncio: AnuncioCard): void {
    const fonte = this.fonte();
    if (fonte.tipo === 'vitrine' && fonte.filtro.escopo === 'favoritos' && !anuncio.favorito) {
      this.itens.update((atuais) => atuais.filter((a) => a.id !== anuncio.id));
      return;
    }
    this.itens.update((atuais) => atuais.map((a) => (a.id === anuncio.id ? anuncio : a)));
  }
}
