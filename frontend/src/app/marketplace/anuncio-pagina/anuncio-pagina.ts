import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, forkJoin, map, merge, of, switchMap, tap } from 'rxjs';
import { ProblemaApi } from '../../auth/sessao';
import { corDaBolha } from '../../bolhas/bolha.service';
import { primeiroNome } from '../../conexoes/conexao.service';
import { BotaoMensagem } from '../../mensagens/botao-mensagem';
import { TrilhaService } from '../../layout/trilha.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { dataCompleta, tempoRelativo } from '../../shared/tempo-relativo';
import { ToastService } from '../../shared/toast.service';
import {
  VisualizadorService,
  descreverFoto,
  rotuloParaAmpliar,
} from '../../shared/visualizador/visualizador.service';
import {
  AnuncioDetalhe,
  AnuncioService,
  Avaliacao,
  TAMANHO_COMENTARIO_AVALIACAO,
  avaliacoesPorExtenso,
  rotuloDoTipoDeAnuncio,
} from '../anuncio.service';
import { gradienteDaCapa, notaPorExtenso, precoPorExtenso, textoDaCapa } from '../preco';

/**
 * O `ServiceDetailScreen` do MVP com dados reais: as fotos, quem anunciou, as bolhas, a descrição, as
 * avaliações e o card de compra.
 *
 * Do MVP saíram os pacotes, o prazo de entrega e as revisões, que o anúncio não tem; no lugar deles, a
 * linha de detalhes mostra o tipo, a categoria e a data. O "Conversar com" abre a conversa com quem anunciou,
 * com um texto sobre o anúncio já no campo (não é enviado sozinho). Para quem anunciou, o card de compra vira o
 * de gerenciar: editar, pausar e excluir.
 *
 * As miniaturas trocam a foto grande, e clicar na foto grande abre todas em tela cheia, a partir dela.
 */
@Component({
  selector: 'app-anuncio-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone, BotaoMensagem],
  styles: ':host { display: contents; }',
  templateUrl: './anuncio-pagina.html',
})
export class AnuncioPagina {
  private readonly servico = inject(AnuncioService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly toast = inject(ToastService);
  private readonly trilhas = inject(TrilhaService);
  private readonly visualizador = inject(VisualizadorService);

  protected readonly anuncio = signal<AnuncioDetalhe | null>(null);
  protected readonly naoEncontrado = signal(false);
  protected readonly avaliacoes = signal<Avaliacao[]>([]);
  /** A foto em destaque, pela posição. */
  protected readonly fotoAtual = signal(0);

  // Avaliação de quem está vendo.
  protected readonly nota = signal(0);
  protected readonly notaSobMouse = signal(0);
  protected readonly comentario = signal('');
  protected readonly salvandoAvaliacao = signal(false);
  protected readonly erroAvaliacao = signal<string | null>(null);

  // Gerenciar, para quem anunciou.
  protected readonly alterandoPausa = signal(false);
  protected readonly confirmandoExclusao = signal(false);
  protected readonly excluindo = signal(false);

  private readonly recarregar = new Subject<void>();

  protected readonly estrelas = [1, 2, 3, 4, 5];
  protected readonly tamanhoComentario = TAMANHO_COMENTARIO_AVALIACAO;
  protected readonly notaPorExtenso = notaPorExtenso;
  protected readonly avaliacoesPorExtenso = avaliacoesPorExtenso;
  protected readonly rotuloDoTipo = rotuloDoTipoDeAnuncio;
  protected readonly tempoRelativo = tempoRelativo;
  protected readonly dataCompleta = dataCompleta;
  protected readonly corDaBolha = corDaBolha;

  protected readonly preco = computed(() => {
    const a = this.anuncio();
    return precoPorExtenso(a?.modalidadePreco, a?.preco);
  });
  protected readonly capa = computed(() => {
    const a = this.anuncio();
    if (!a) {
      return null;
    }
    return {
      url: a.imagens[this.fotoAtual()] ?? a.imagens[0] ?? null,
      fundo: gradienteDaCapa(a.id),
      texto: textoDaCapa(a.titulo),
    };
  });
  protected readonly primeiroNome = computed(() => primeiroNome(this.anuncio()?.anunciante.nomeCompleto ?? ''));
  /** O texto que já vem no campo da conversa aberta pelo "Conversar com". */
  protected readonly rascunho = computed(
    () => `Oi, ${this.primeiroNome()}! Vi seu anúncio "${this.anuncio()?.titulo ?? ''}" no marketplace.`,
  );
  protected readonly publicadoEm = computed(() => {
    const quando = this.anuncio()?.publicadoEm;
    return quando
      ? new Date(quando).toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' })
      : '—';
  });
  /** A nota que as estrelas mostram: a do mouse, enquanto ele passa por cima, ou a escolhida. */
  protected readonly notaVisivel = computed(() => this.notaSobMouse() || this.nota());

  constructor() {
    const id$ = inject(ActivatedRoute).paramMap.pipe(map((parametros) => parametros.get('id') ?? ''));
    merge(
      id$.pipe(
        tap(() => {
          this.anuncio.set(null);
          this.naoEncontrado.set(false);
          this.fotoAtual.set(0);
          this.confirmandoExclusao.set(false);
        }),
      ),
      this.recarregar.pipe(map(() => this.anuncio()?.id ?? '')),
    )
      .pipe(
        switchMap((id) =>
          forkJoin({
            anuncio: this.servico.buscar(id),
            avaliacoes: this.servico.avaliacoes(id).pipe(catchError(() => of([] as Avaliacao[]))),
          }).pipe(
            catchError((erro: unknown) => {
              if (erro instanceof HttpErrorResponse && erro.status === 404) {
                this.naoEncontrado.set(true);
                this.trilhas.definir(['Marketplace', 'Anúncio não encontrado']);
              } else {
                this.toast.mostrar('Não foi possível carregar o anúncio. Tente de novo.');
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((resposta) => {
        if (resposta) {
          this.mostrar(resposta.anuncio, resposta.avaliacoes);
        }
      });
  }

  protected rotuloDaFoto(anuncio: AnuncioDetalhe): string {
    return rotuloParaAmpliar(this.fotoAtual(), anuncio.imagens.length, `do anúncio ${anuncio.titulo}`);
  }

  /** Todas as fotos do anúncio em tela cheia, a partir da que está na tela. */
  protected verFotos(anuncio: AnuncioDetalhe): void {
    const total = anuncio.imagens.length;
    this.visualizador.abrir(
      anuncio.imagens.map((url, i) => ({ url, descricao: descreverFoto(i, total, `do anúncio ${anuncio.titulo}`) })),
      this.fotoAtual(),
    );
  }

  protected voltar(): void {
    if (history.length > 1) {
      this.location.back();
    } else {
      void this.router.navigate(['/marketplace']);
    }
  }

  // ---------------------------------------------------------------- favorito e compartilhar

  protected favoritar(): void {
    const antes = this.anuncio();
    if (!antes) {
      return;
    }
    const ligar = !antes.favorito;
    this.anuncio.set({ ...antes, favorito: ligar });
    this.toast.mostrar(ligar ? 'Salvo nos favoritos' : 'Removido dos favoritos');
    this.servico.favoritar(antes.id, ligar).subscribe({
      error: () => {
        this.anuncio.update((atual) => (atual ? { ...atual, favorito: antes.favorito } : atual));
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
      },
    });
  }

  protected async compartilhar(): Promise<void> {
    const endereco = `${location.origin}/marketplace/${this.anuncio()?.id}`;
    try {
      await navigator.clipboard.writeText(endereco);
      this.toast.mostrar('Link copiado');
    } catch {
      this.toast.mostrar(endereco);
    }
  }

  // ---------------------------------------------------------------- avaliação

  protected salvarAvaliacao(): void {
    const anuncio = this.anuncio();
    if (!anuncio || this.salvandoAvaliacao()) {
      return;
    }
    if (!this.nota()) {
      this.erroAvaliacao.set('Escolha de 1 a 5 estrelas.');
      return;
    }
    this.erroAvaliacao.set(null);
    this.salvandoAvaliacao.set(true);
    this.servico.avaliar(anuncio.id, this.nota(), this.comentario()).subscribe({
      next: () => {
        this.salvandoAvaliacao.set(false);
        this.toast.mostrar(anuncio.minhaAvaliacao ? 'Avaliação atualizada' : 'Obrigado pela avaliação');
        this.recarregar.next();
      },
      error: (erro: unknown) => {
        this.salvandoAvaliacao.set(false);
        this.erroAvaliacao.set(mensagemDoErro(erro));
      },
    });
  }

  protected apagarAvaliacao(avaliacao: Avaliacao): void {
    const anuncio = this.anuncio();
    if (!anuncio) {
      return;
    }
    this.servico.apagarAvaliacao(anuncio.id, avaliacao.id).subscribe({
      next: () => {
        this.toast.mostrar('Avaliação apagada');
        this.recarregar.next();
      },
      error: () => this.toast.mostrar('Não foi possível apagar a avaliação. Tente de novo.'),
    });
  }

  // ---------------------------------------------------------------- gerenciar

  protected alternarPausa(): void {
    const anuncio = this.anuncio();
    if (!anuncio || this.alterandoPausa()) {
      return;
    }
    const pausar = anuncio.status === 'PUBLICADO';
    this.alterandoPausa.set(true);
    this.servico.pausar(anuncio.id, pausar).subscribe({
      next: () => {
        this.alterandoPausa.set(false);
        this.anuncio.set({ ...anuncio, status: pausar ? 'PAUSADO' : 'PUBLICADO' });
        this.toast.mostrar(pausar ? 'Anúncio pausado: só você vê' : 'Anúncio de volta no marketplace');
      },
      error: () => {
        this.alterandoPausa.set(false);
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
      },
    });
  }

  protected excluir(): void {
    const anuncio = this.anuncio();
    if (!anuncio || this.excluindo()) {
      return;
    }
    this.excluindo.set(true);
    this.servico.excluir(anuncio.id).subscribe({
      next: () => {
        this.toast.mostrar('Anúncio excluído');
        void this.router.navigateByUrl('/marketplace');
      },
      error: () => {
        this.excluindo.set(false);
        this.toast.mostrar('Não foi possível excluir. Tente de novo.');
      },
    });
  }

  // ---------------------------------------------------------------- apoio

  /** "★★★★" e "★" apagada, como as `.review-stars` do MVP. */
  protected cheias(nota: number): string {
    return '★'.repeat(nota);
  }

  protected vazias(nota: number): string {
    return '★'.repeat(5 - nota);
  }

  private mostrar(anuncio: AnuncioDetalhe, avaliacoes: Avaliacao[]): void {
    this.anuncio.set(anuncio);
    this.avaliacoes.set(avaliacoes);
    if (this.fotoAtual() >= anuncio.imagens.length) {
      this.fotoAtual.set(0);
    }
    this.nota.set(anuncio.minhaAvaliacao?.nota ?? 0);
    this.comentario.set(anuncio.minhaAvaliacao?.comentario ?? '');
    this.trilhas.definir(['Marketplace', anuncio.titulo]);
  }
}

/** A mensagem da API para o formulário de avaliação. */
function mensagemDoErro(erro: unknown): string {
  const problema = erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
  const doCampo = problema?.erros ? Object.values(problema.erros)[0] : null;
  return doCampo ?? problema?.detail ?? 'Não foi possível salvar a avaliação. Tente de novo.';
}
