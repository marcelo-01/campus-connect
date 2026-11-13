import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { PublicacaoCard } from '../publicacao-card/publicacao-card';
import {
  EscopoFeed,
  ItemFeed,
  NovasPublicacoes,
  OrdemFeed,
  PaginaFeed,
  Publicacao,
  PublicacaoService,
  rotuloDasNovas,
} from '../publicacao.service';
import { verificacoes, visibilidade } from './verificacoes';

const CHIPS: { ordem: OrdemFeed; rotulo: string }[] = [
  { ordem: 'recentes', rotulo: 'Mais recente' },
  { ordem: 'alta', rotulo: 'Em alta' },
  { ordem: 'midia', rotulo: 'Com mídia' },
];

/** De onde o aviso conta os posts novos: a marca `topo` da primeira página e o feed em que ela veio. */
interface Marca {
  escopo: EscopoFeed;
  ordem: OrdemFeed;
  topo: string;
}

/**
 * A lista de posts do `FeedScreen` do MVP: os chips de ordem, os cards e o estado vazio. Carrega mais ao
 * chegar perto do fim da página (e pelo botão "Ver mais", para quem navega pelo teclado).
 *
 * O chip "Enquetes" do MVP saiu: enquete não é requisito. Quem usa o feed pode pôr um filtro antes dos chips
 * com o atributo `filtro`, como o seletor de bolha do Início.
 *
 * No Início e na bolha, um aviso no topo conta os posts que chegaram depois que o feed abriu, como no X. O
 * feed pergunta ao servidor de tempos em tempos, só com a aba visível, e nunca insere posts sozinho: no
 * clique, a primeira página vem de novo e o feed volta ao topo.
 */
@Component({
  selector: 'app-feed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Avatar, Icone, PublicacaoCard],
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
  /** O aviso de posts novos; `null` enquanto não chega nenhum. */
  protected readonly novas = signal<NovasPublicacoes | null>(null);
  protected readonly carregandoNovas = signal(false);
  protected readonly rotuloDasNovas = rotuloDasNovas;

  /** Muda a cada "Tentar de novo", para o pedido sair outra vez. */
  private readonly tentativa = signal(0);
  /** Muda a cada recarga: uma página que chega atrasada, de antes da troca, é descartada. */
  private geracao = 0;
  /**
   * O post mais novo que veio do servidor. O post que a própria pessoa publica entra no topo pelo
   * `adicionar` sem mudar a marca: um post de outra pessoa feito antes do dela ainda é novo.
   */
  private readonly marca = signal<Marca | null>(null);
  private readonly fim = viewChild<ElementRef<HTMLElement>>('fim');
  /** A linha dos chips, onde o feed começa. */
  private readonly comeco = viewChild<ElementRef<HTMLElement>>('comeco');

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
          this.carregandoNovas.set(false);
          this.falhou.set(false);
          this.itens.set([]);
          this.proximo.set(null);
          this.marca.set(null);
          this.novas.set(null);
        }),
        switchMap(({ escopo, ordem }) =>
          this.servico.pagina(escopo, ordem, null).pipe(
            map((pagina) => ({ pagina, escopo, ordem })),
            catchError(() => {
              this.falhou.set(true);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((resposta) => {
        this.carregando.set(false);
        if (resposta) {
          this.mostrar(resposta.pagina, resposta.escopo, resposta.ordem);
        }
      });

    // Posts novos: com a marca do topo, pergunta de tempos em tempos se chegou algo depois dela. Uma
    // resposta que chega depois de a marca mudar é de outra lista e fica de fora.
    const visivel = visibilidade(inject(DOCUMENT));
    toObservable(this.marca)
      .pipe(
        switchMap((marca) =>
          marca
            ? verificacoes(visivel).pipe(
                exhaustMap(() =>
                  this.servico
                    .novas(marca.escopo, marca.ordem, marca.topo)
                    .pipe(catchError(() => EMPTY)),
                ),
                map((novas) => ({ marca, novas })),
              )
            : EMPTY,
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ marca, novas }) => {
        if (marca === this.marca()) {
          this.novas.set(novas.total > 0 ? novas : null);
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
    if (!cursor || this.carregandoMais() || this.carregando() || this.carregandoNovas()) {
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

  /** O clique no aviso: a primeira página vem de novo, já com os posts novos, e o feed volta ao topo. */
  protected verNovas(): void {
    const marca = this.marca();
    if (!marca || this.carregandoNovas()) {
      return;
    }
    // Um "Ver mais" que ainda estava a caminho é descartado: a lista vai ser outra.
    const geracao = ++this.geracao;
    this.carregandoMais.set(false);
    this.carregandoNovas.set(true);
    this.servico.pagina(marca.escopo, marca.ordem, null).subscribe({
      next: (pagina) => {
        if (geracao !== this.geracao) {
          return;
        }
        this.carregandoNovas.set(false);
        this.mostrar(pagina, marca.escopo, marca.ordem);
        this.voltarAoTopo();
      },
      error: () => {
        if (geracao === this.geracao) {
          this.carregandoNovas.set(false);
          this.toast.mostrar('Não foi possível carregar as publicações novas. Tente de novo.');
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

  /** A primeira página, com a marca do topo. Uma marca nova zera o aviso. */
  private mostrar(pagina: PaginaFeed, escopo: EscopoFeed, ordem: OrdemFeed): void {
    this.itens.set(pagina.itens);
    this.proximo.set(pagina.proximo);
    this.marca.set(pagina.topo ? { escopo, ordem, topo: pagina.topo } : null);
    this.novas.set(null);
  }

  /** Rola até os chips, mas só se o começo do feed já saiu da tela. */
  private voltarAoTopo(): void {
    const comeco = this.comeco()?.nativeElement;
    if (!comeco || comeco.getBoundingClientRect().top >= 0) {
      return;
    }
    const semAnimacao =
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    comeco.scrollIntoView({ behavior: semAnimacao ? 'auto' : 'smooth', block: 'start' });
  }
}

function chave(item: ItemFeed): string {
  return `${item.publicacao.id}:${item.republicadaPor?.id ?? ''}`;
}
