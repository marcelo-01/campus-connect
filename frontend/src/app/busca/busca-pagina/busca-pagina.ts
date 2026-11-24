import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, debounceTime, distinctUntilChanged, map, skip, switchMap, tap } from 'rxjs';
import { BolhaCartao } from '../../bolhas/bolha-cartao';
import { BolhaResumo, BolhaService, casaComBusca } from '../../bolhas/bolha.service';
import { PessoaLinha } from '../../conexoes/pessoa-linha';
import { Feed } from '../../publicacoes/feed/feed';
import { EscopoFeed, PessoaPublica } from '../../publicacoes/publicacao.service';
import { Icone } from '../../shared/icone';
import { ABAS_BUSCA, AbaBusca, BuscaService, TAMANHO_BUSCA, abaDaUrl, enderecoDaBusca } from '../busca.service';

/**
 * Os resultados da busca, em `/busca?q=&aba=`, com uma aba para posts, outra para pessoas e outra para bolhas. O
 * campo da barra do topo leva para cá no Enter. Os posts vêm de toda a rede, por palavras e do mais novo para o mais
 * antigo; as pessoas, pelo nome e pelo username; as bolhas, pelo nome, pela sigla e pela descrição, como no Explorar.
 *
 * Nas telas estreitas, a barra do topo não tem o campo de busca, e a página mostra o dela, que busca enquanto a
 * pessoa digita. O termo fica na URL, então sobrevive ao recarregar a página e vai junto num link copiado.
 */
@Component({
  selector: 'app-busca-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BolhaCartao, Feed, Icone, PessoaLinha],
  styles: ':host { display: contents; }',
  templateUrl: './busca-pagina.html',
})
export class BuscaPagina {
  private readonly router = inject(Router);
  private readonly busca = inject(BuscaService);
  private readonly bolhasServico = inject(BolhaService);
  private readonly parametros = toSignal(inject(ActivatedRoute).queryParamMap, { requireSync: true });

  protected readonly abas = ABAS_BUSCA;
  protected readonly tamanhoBusca = TAMANHO_BUSCA;
  protected readonly termo = computed(() => (this.parametros().get('q') ?? '').trim());
  protected readonly aba = computed(() => abaDaUrl(this.parametros().get('aba')));

  /**
   * O que está no campo da página, que vai para a URL depois de uma pausa. Quando a URL muda por outro caminho, o
   * campo a acompanha, a não ser que ela só tenha alcançado o que a pessoa digitou.
   */
  protected readonly digitado = linkedSignal<string, string>({
    source: this.termo,
    computation: (termo, anterior) => (anterior && anterior.value.trim() === termo ? anterior.value : termo),
  });

  protected readonly escopoDosPosts = computed<EscopoFeed>(() => ({ tipo: 'busca', termo: this.termo() }));
  protected readonly semPosts = computed(
    () => `Nenhum post com “${this.termo()}”. Tente outras palavras ou veja se há pessoas e bolhas com esse nome.`,
  );

  /** `null` enquanto a primeira página não chega. */
  protected readonly pessoas = signal<PessoaPublica[] | null>(null);
  protected readonly proximaDePessoas = signal<string | null>(null);
  protected readonly carregandoMaisPessoas = signal(false);
  protected readonly falhouPessoas = signal(false);

  /** Todas as bolhas, buscadas uma vez, quando a aba abre. */
  private readonly todasAsBolhas = signal<BolhaResumo[] | null>(null);
  protected readonly falhouBolhas = signal(false);
  protected readonly bolhas = computed(() =>
    this.todasAsBolhas()?.filter((bolha) => casaComBusca(bolha, this.termo())),
  );
  private readonly bolhaPorId = computed(() => new Map((this.todasAsBolhas() ?? []).map((b) => [b.id, b])));

  /** Muda a cada "Tentar de novo", para o pedido sair outra vez. */
  private readonly tentativa = signal(0);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  constructor() {
    // A primeira página de pessoas, de novo a cada termo, só com a aba aberta.
    const pedidoDePessoas = computed(
      () => ({ termo: this.termo(), aberta: this.aba() === 'pessoas', tentativa: this.tentativa() }),
      { equal: (a, b) => a.termo === b.termo && a.aberta === b.aberta && a.tentativa === b.tentativa },
    );
    toObservable(pedidoDePessoas)
      .pipe(
        tap(() => {
          this.pessoas.set(null);
          this.proximaDePessoas.set(null);
          this.carregandoMaisPessoas.set(false);
          this.falhouPessoas.set(false);
        }),
        switchMap(({ termo, aberta }) => {
          if (!aberta || !termo) {
            return EMPTY;
          }
          return this.busca.pessoas(termo).pipe(
            catchError(() => {
              this.falhouPessoas.set(true);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        this.pessoas.set(pagina.itens);
        this.proximaDePessoas.set(pagina.proximo);
      });

    toObservable(computed(() => this.aba() === 'bolhas' && this.termo() !== ''))
      .pipe(takeUntilDestroyed())
      .subscribe((aberta) => {
        if (aberta && this.todasAsBolhas() === null) {
          this.carregarBolhas();
        }
      });

    // O campo da página leva o termo para a URL depois de uma pausa na digitação.
    toObservable(this.digitado)
      .pipe(
        skip(1),
        debounceTime(300),
        map((texto) => texto.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((texto) => this.irPara(texto));

    // Quem chega pela lupa da barra do topo, sem termo, já encontra o campo pronto para digitar.
    const janela = inject(DOCUMENT).defaultView;
    afterNextRender(() => {
      const campo = this.campo()?.nativeElement;
      if (!this.termo() && campo && campo.offsetParent !== null && janela?.matchMedia?.('(max-width: 1023px)').matches) {
        campo.focus();
      }
    });
  }

  protected escolherAba(aba: AbaBusca): void {
    const { caminho, queryParams } = enderecoDaBusca(this.termo(), aba);
    void this.router.navigate(caminho, { queryParams });
  }

  protected digitar(evento: Event): void {
    this.digitado.set((evento.target as HTMLInputElement).value);
  }

  /** O Enter no campo da página busca na hora e fecha o teclado do celular. */
  protected buscarAgora(campo: HTMLInputElement): void {
    this.irPara(campo.value.trim());
    campo.blur();
  }

  protected carregarMaisPessoas(): void {
    const cursor = this.proximaDePessoas();
    const termo = this.termo();
    if (!cursor || this.carregandoMaisPessoas()) {
      return;
    }
    this.carregandoMaisPessoas.set(true);
    this.busca.pessoas(termo, cursor).subscribe({
      next: (pagina) => {
        // Uma página que chega depois de o termo mudar é de outra busca.
        if (termo !== this.termo()) {
          return;
        }
        this.pessoas.update((atuais) => [...(atuais ?? []), ...pagina.itens]);
        this.proximaDePessoas.set(pagina.proximo);
        this.carregandoMaisPessoas.set(false);
      },
      error: () => this.carregandoMaisPessoas.set(false),
    });
  }

  protected tentarDeNovo(): void {
    this.tentativa.update((n) => n + 1);
  }

  protected carregarBolhas(): void {
    this.falhouBolhas.set(false);
    this.bolhasServico.todas().subscribe({
      next: (bolhas) => this.todasAsBolhas.set(bolhas),
      error: () => this.falhouBolhas.set(true),
    });
  }

  protected nomeAcima(bolha: BolhaResumo): string | undefined {
    return bolha.bolhaPaiId ? this.bolhaPorId().get(bolha.bolhaPaiId)?.nome : undefined;
  }

  /** A busca troca a entrada do histórico, para não encher o "voltar" com cada pausa na digitação. */
  private irPara(termo: string): void {
    if (termo === this.termo()) {
      return;
    }
    const { caminho, queryParams } = enderecoDaBusca(termo, this.aba());
    void this.router.navigate(caminho, { queryParams, replaceUrl: true });
  }
}
