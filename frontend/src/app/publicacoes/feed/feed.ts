import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, tap } from 'rxjs';
import { ToastService } from '../../shared/toast.service';
import { PublicacaoCard } from '../publicacao-card/publicacao-card';
import {
  EscopoFeed,
  ItemFeed,
  OrdemFeed,
  Publicacao,
  PublicacaoService,
} from '../publicacao.service';

const CHIPS: { ordem: OrdemFeed; rotulo: string }[] = [
  { ordem: 'recentes', rotulo: 'Mais recente' },
  { ordem: 'alta', rotulo: 'Em alta' },
  { ordem: 'midia', rotulo: 'Com mídia' },
];

/**
 * A lista de posts do `FeedScreen` do MVP: os chips de ordem, os cards e o estado vazio. Carrega mais ao
 * chegar perto do fim da página (e pelo botão "Ver mais", para quem navega pelo teclado).
 *
 * O chip "Enquetes" do MVP saiu: enquete não é requisito. Quem usa o feed pode pôr um filtro antes dos chips
 * com o atributo `filtro`, como o seletor de bolha do Início.
 */
@Component({
  selector: 'app-feed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PublicacaoCard],
  styles: ':host { display: contents; }',
  templateUrl: './feed.html',
})
export class Feed {
  private readonly servico = inject(PublicacaoService);
  private readonly toast = inject(ToastService);

  readonly escopo = input.required<EscopoFeed>();
  /** O perfil e as salvas não têm chips: a ordem é sempre a da data. */
  readonly comChips = input(true);
  /** `bolha` é o estado vazio do feed do MVP; `cartao` é o do perfil. */
  readonly estiloVazio = input<'bolha' | 'cartao'>('bolha');
  readonly vazioTitulo = input('Nada por aqui ainda');
  readonly vazioTexto = input('');
  readonly vazioSigla = input('CC');
  readonly vazioCor = input('var(--accent)');
  /**
   * Quando muda, o feed recarrega do começo. A página da bolha passa se a pessoa é membro: depois de
   * entrar, os posts voltam com a permissão de curtir e comentar.
   */
  readonly versao = input<unknown>(null);

  protected readonly chips = CHIPS;
  protected readonly ordem = signal<OrdemFeed>('recentes');
  protected readonly itens = signal<ItemFeed[]>([]);
  protected readonly proximo = signal<string | null>(null);
  protected readonly carregando = signal(true);
  protected readonly carregandoMais = signal(false);
  protected readonly falhou = signal(false);

  /** Muda a cada "Tentar de novo", para o pedido sair outra vez. */
  private readonly tentativa = signal(0);
  /** Muda a cada recarga: uma página que chega atrasada, de antes da troca, é descartada. */
  private geracao = 0;
  private readonly fim = viewChild<ElementRef<HTMLElement>>('fim');

  constructor() {
    const pedido = computed(
      () => ({
        escopo: this.escopo(),
        ordem: this.ordem(),
        versao: this.versao(),
        tentativa: this.tentativa(),
      }),
      { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
    );
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
        switchMap(({ escopo, ordem }) =>
          this.servico.pagina(escopo, ordem, null).pipe(
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

  /** Um post que quem está logado acabou de publicar entra no topo. */
  adicionar(publicacao: Publicacao): void {
    if (this.ordem() !== 'recentes') {
      this.ordem.set('recentes'); // recarrega, já com o post novo
      return;
    }
    this.itens.update((atuais) => [
      { publicacao, republicadaPor: null, em: publicacao.criadoEm },
      ...atuais,
    ]);
  }

  protected carregarMais(): void {
    const cursor = this.proximo();
    if (!cursor || this.carregandoMais() || this.carregando()) {
      return;
    }
    const geracao = this.geracao;
    this.carregandoMais.set(true);
    this.servico.pagina(this.escopo(), this.ordem(), cursor).subscribe({
      next: (pagina) => {
        if (geracao !== this.geracao) {
          return;
        }
        this.itens.update((atuais) => {
          const vistas = new Set(atuais.map(chave));
          return [...atuais, ...pagina.itens.filter((item) => !vistas.has(chave(item)))];
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

  /** O card mudou (curtida, edição...): o mesmo post pode aparecer de novo mais abaixo. */
  protected atualizar(publicacao: Publicacao): void {
    const escopo = this.escopo();
    if (escopo.tipo === 'salvas' && !publicacao.salva) {
      this.remover(publicacao.id);
      return;
    }
    this.itens.update((atuais) =>
      atuais.map((item) => (item.publicacao.id === publicacao.id ? { ...item, publicacao } : item)),
    );
  }

  protected remover(publicacaoId: string): void {
    this.itens.update((atuais) => atuais.filter((item) => item.publicacao.id !== publicacaoId));
  }

  protected readonly chave = chave;
}

function chave(item: ItemFeed): string {
  return `${item.publicacao.id}:${item.republicadaPor?.id ?? ''}`;
}
