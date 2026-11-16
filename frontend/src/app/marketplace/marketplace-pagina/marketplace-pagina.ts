import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, skip } from 'rxjs';
import { BolhaService, recuoNoSelect } from '../../bolhas/bolha.service';
import { Icone } from '../../shared/icone';
import {
  AnuncioService,
  EscopoVitrine,
  FiltroVitrine,
  FonteAnuncios,
  OrdemVitrine,
  TipoAnuncio,
  anunciosPorExtenso,
} from '../anuncio.service';
import { ListaAnuncios } from '../lista-anuncios/lista-anuncios';

const ESCOPOS: EscopoVitrine[] = ['bolhas', 'rede', 'favoritos'];
const ORDENS: { valor: OrdemVitrine; rotulo: string }[] = [
  { valor: 'recentes', rotulo: 'Mais recentes' },
  { valor: 'preco-asc', rotulo: 'Menor preço' },
  { valor: 'preco-desc', rotulo: 'Maior preço' },
  { valor: 'avaliacao', rotulo: 'Melhor avaliados' },
];

/**
 * O `MarketScreen` do MVP com dados reais. No lugar de "Nesta bolha", que dependia da bolha ativa do MVP,
 * o toggle tem "Minhas bolhas" (o que foi direcionado às bolhas em que a pessoa está), "Toda a rede" e
 * "Favoritos". Em "Minhas bolhas", um seletor filtra por uma bolha só, como no Início.
 *
 * Os filtros ficam na URL (`?escopo=&bolha=&tipo=&categoria=&q=&ordem=`), então sobrevivem ao recarregar a
 * página. Antes dos chips de categoria entra o seletor de tipo: serviço ou produto.
 */
@Component({
  selector: 'app-marketplace-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icone, ListaAnuncios],
  styles: ':host { display: contents; }',
  templateUrl: './marketplace-pagina.html',
})
export class MarketplacePagina {
  private readonly router = inject(Router);
  private readonly bolhas = inject(BolhaService);
  private readonly anuncios = inject(AnuncioService);

  private readonly parametros = toSignal(inject(ActivatedRoute).queryParamMap, { requireSync: true });

  protected readonly ordens = ORDENS;
  protected readonly recuo = recuoNoSelect;
  protected readonly anunciosPorExtenso = anunciosPorExtenso;
  /** `null` enquanto o menu lateral ainda carrega as bolhas. */
  protected readonly minhas = this.bolhas.minhas;
  protected readonly categorias = this.anuncios.categorias;
  /** Quantos anúncios passam no filtro; chega com a primeira página. */
  protected readonly total = signal<number | null>(null);

  protected readonly escopo = computed<EscopoVitrine>(() => {
    const escopo = this.parametros().get('escopo') as EscopoVitrine | null;
    return escopo && ESCOPOS.includes(escopo) ? escopo : 'bolhas';
  });
  protected readonly tipo = computed<TipoAnuncio | null>(() => {
    const tipo = this.parametros().get('tipo');
    return tipo === 'SERVICO' || tipo === 'PRODUTO' ? tipo : null;
  });
  protected readonly ordem = computed<OrdemVitrine>(() => {
    const ordem = this.parametros().get('ordem');
    return ORDENS.find((opcao) => opcao.valor === ordem)?.valor ?? 'recentes';
  });
  protected readonly categoria = computed(() => {
    const slug = this.parametros().get('categoria');
    return slug ? (this.categorias()?.find((c) => c.slug === slug) ?? null) : null;
  });
  /** Os chips de categoria: só as do tipo escolhido, ou todas. */
  protected readonly chipsDeCategoria = computed(() => {
    const tipo = this.tipo();
    return (this.categorias() ?? []).filter((c) => !tipo || c.tipo === tipo);
  });
  /** A bolha do seletor, só em "Minhas bolhas"; uma que a pessoa deixou fica de fora. */
  protected readonly bolha = computed(() => {
    const slug = this.parametros().get('bolha');
    if (!slug || this.escopo() !== 'bolhas') {
      return null;
    }
    return this.minhas()?.find((b) => b.slug === slug) ?? null;
  });
  protected readonly busca = computed(() => this.parametros().get('q') ?? '');
  /** O que está no campo de busca agora; vai para a URL depois de uma pausa na digitação. */
  protected readonly digitado = signal(this.busca());

  /**
   * A lista só pede a primeira página quando dá para montar o filtro: com uma bolha na URL, espera as
   * bolhas da pessoa chegarem, para não buscar duas vezes.
   */
  protected readonly fonte = computed<FonteAnuncios | null>(() => {
    const slugDaBolha = this.parametros().get('bolha');
    if (slugDaBolha && this.escopo() === 'bolhas' && this.minhas() === null) {
      return null;
    }
    const filtro: FiltroVitrine = {
      escopo: this.escopo(),
      bolhaId: this.bolha()?.id ?? null,
      tipo: this.tipo(),
      categoria: this.parametros().get('categoria'),
      busca: this.busca(),
      ordem: this.ordem(),
    };
    return { tipo: 'vitrine', filtro };
  });

  /** "em todas as categorias", "em Design". */
  protected readonly ondeCategoria = computed(() => this.categoria()?.nome ?? 'todas as categorias');
  /** "suas bolhas", "ADS", "toda a rede", "seus favoritos". */
  protected readonly ondeEscopo = computed(() => {
    switch (this.escopo()) {
      case 'rede':
        return 'em toda a rede';
      case 'favoritos':
        return 'nos seus favoritos';
      default:
        return `em ${this.bolha()?.nome ?? 'suas bolhas'}`;
    }
  });
  /** Quem não está em nenhuma bolha não tem o que ver em "Minhas bolhas". */
  protected readonly semBolhas = computed(() => this.escopo() === 'bolhas' && this.minhas()?.length === 0);

  constructor() {
    this.anuncios.carregarCategorias();
    toObservable(this.digitado)
      .pipe(
        skip(1),
        debounceTime(300),
        map((texto) => texto.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((texto) => this.filtrar({ q: texto || null }, true));
  }

  protected escolherEscopo(escopo: EscopoVitrine): void {
    this.filtrar({ escopo: escopo === 'bolhas' ? null : escopo, bolha: null });
  }

  protected escolherBolha(slug: string): void {
    this.filtrar({ bolha: slug || null });
  }

  /** Trocar o tipo tira a categoria, que pode ser do outro tipo. */
  protected escolherTipo(tipo: string): void {
    this.filtrar({ tipo: tipo || null, categoria: null });
  }

  protected escolherCategoria(slug: string | null): void {
    this.filtrar({ categoria: slug });
  }

  protected escolherOrdem(ordem: string): void {
    this.filtrar({ ordem: ordem === 'recentes' ? null : ordem });
  }

  protected digitar(evento: Event): void {
    this.digitado.set((evento.target as HTMLInputElement).value);
  }

  /** `null` tira o parâmetro da URL. A busca troca a entrada do histórico, para não encher o "voltar". */
  private filtrar(mudanca: Params, substituir = false): void {
    void this.router.navigate([], {
      queryParams: mudanca,
      queryParamsHandling: 'merge',
      replaceUrl: substituir,
    });
  }
}
