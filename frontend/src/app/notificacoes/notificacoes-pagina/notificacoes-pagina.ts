import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Avatar } from '../../shared/avatar';
import { Icone, NomeIcone } from '../../shared/icone';
import { dataCompleta, tempoRelativo } from '../../shared/tempo-relativo';
import { ToastService } from '../../shared/toast.service';
import {
  Notificacao,
  NotificacaoService,
  PaginaNotificacoes,
  Parte,
  acao,
  autores,
  destino,
  quemFez,
} from '../notificacao.service';

/**
 * As notificações, das mais novas para as mais antigas. O MVP não tinha esta tela: o sino e o item do menu só
 * mostravam um aviso de rascunho.
 *
 * Abrir a tela marca como lidas as que ela mostrou, e as que eram novas continuam destacadas até a pessoa sair da
 * tela. Se outra chega com a tela aberta, aparece um aviso no topo, como o de posts novos do feed: a lista não
 * muda sozinha embaixo de quem está lendo.
 *
 * Carrega mais ao chegar perto do fim da página (e pelo botão "Ver mais", para quem navega pelo teclado).
 */
@Component({
  selector: 'app-notificacoes-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './notificacoes-pagina.html',
})
export class NotificacoesPagina {
  private readonly servico = inject(NotificacaoService);
  private readonly toast = inject(ToastService);
  private readonly destruida = inject(DestroyRef);

  protected readonly itens = signal<Notificacao[]>([]);
  protected readonly proximo = signal<string | null>(null);
  protected readonly carregando = signal(true);
  protected readonly falhou = signal(false);
  protected readonly carregandoMais = signal(false);
  protected readonly recarregando = signal(false);

  /** Enquanto a página não foi marcada como lida, o contador ainda conta o que já está na tela. */
  private readonly marcando = signal(true);
  /** As que vieram como novas nesta visita. Continuam destacadas depois de marcadas como lidas. */
  private readonly novasDaVisita = signal<ReadonlySet<string>>(new Set());
  /** Chegou notificação com a tela aberta. */
  protected readonly temNovas = computed(
    () => !this.carregando() && !this.marcando() && this.servico.naoLidas() > 0,
  );

  /** Muda a cada recarga: uma página que chega atrasada, de antes da troca, é descartada. */
  private geracao = 0;
  private readonly fim = viewChild<ElementRef<HTMLElement>>('fim');
  private readonly comeco = viewChild<ElementRef<HTMLElement>>('comeco');

  protected readonly acao = acao;
  protected readonly destino = destino;
  protected readonly tempo = (instante: string) => tempoRelativo(instante);
  protected readonly dataCompleta = dataCompleta;

  constructor() {
    this.primeiraPagina();

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

    // Sair da tela com a página a caminho não deixa nada pela metade.
    this.destruida.onDestroy(() => this.geracao++);
  }

  protected destacada(notificacao: Notificacao): boolean {
    return notificacao.nova || this.novasDaVisita().has(notificacao.id);
  }

  /** O texto todo: quem fez, com os nomes em negrito, e o que fez. */
  protected frase(notificacao: Notificacao): Parte[] {
    return [...quemFez(autores(notificacao)), { texto: ` ${acao(notificacao)}`, nome: false }];
  }

  protected icone(notificacao: Notificacao): NomeIcone {
    switch (notificacao.tipo) {
      case 'SOLICITACAO_CONEXAO':
      case 'CONEXAO_ACEITA':
        return 'conexoes';
      case 'NOVO_COMENTARIO':
      case 'RESPOSTA_COMENTARIO':
        return 'comentario';
      case 'CURTIDA_PUBLICACAO':
      case 'CURTIDA_COMENTARIO':
        return 'coracao-cheio';
      case 'REPUBLICACAO':
        return 'republicar';
      case 'AVALIACAO_ANUNCIO':
        return 'estrela';
    }
  }

  /** A cor do ícone: a curtida fica na tangerina, como no card do post, e a avaliação no dourado das estrelas. */
  protected corDoIcone(notificacao: Notificacao): string | null {
    switch (notificacao.tipo) {
      case 'CURTIDA_PUBLICACAO':
      case 'CURTIDA_COMENTARIO':
        return 'curtida';
      case 'AVALIACAO_ANUNCIO':
        return 'avaliacao';
      default:
        return null;
    }
  }

  /** As que chegaram com a tela aberta: a primeira página vem de novo, e a tela volta ao topo. */
  protected verNovas(): void {
    if (this.recarregando()) {
      return;
    }
    this.recarregando.set(true);
    this.primeiraPagina();
  }

  protected tentarDeNovo(): void {
    this.carregando.set(true);
    this.falhou.set(false);
    this.primeiraPagina();
  }

  protected carregarMais(): void {
    const cursor = this.proximo();
    if (!cursor || this.carregandoMais() || this.carregando() || this.recarregando()) {
      return;
    }
    const geracao = this.geracao;
    this.carregandoMais.set(true);
    this.servico.listar(cursor).subscribe({
      next: (pagina) => {
        if (geracao !== this.geracao) {
          return;
        }
        this.itens.update((atuais) => {
          const vistas = new Set(atuais.map((notificacao) => notificacao.id));
          return [...atuais, ...pagina.itens.filter((notificacao) => !vistas.has(notificacao.id))];
        });
        this.lembrarNovas(pagina);
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

  private primeiraPagina(): void {
    const geracao = ++this.geracao;
    const recarga = !this.carregando();
    this.carregandoMais.set(false);
    this.marcando.set(true);
    this.servico.listar().subscribe({
      next: (pagina) => {
        if (geracao !== this.geracao) {
          return;
        }
        this.carregando.set(false);
        this.recarregando.set(false);
        this.itens.set(pagina.itens);
        this.proximo.set(pagina.proximo);
        this.lembrarNovas(pagina);
        this.marcarLidas(pagina, geracao);
        if (recarga) {
          this.voltarAoTopo();
        }
      },
      error: () => {
        if (geracao !== this.geracao) {
          return;
        }
        this.marcando.set(false);
        if (recarga) {
          this.recarregando.set(false);
          this.toast.mostrar('Não foi possível carregar as notificações novas. Tente de novo.');
        } else {
          this.carregando.set(false);
          this.falhou.set(true);
        }
      },
    });
  }

  /** Marca como lidas até a mais nova da página, se havia alguma por ler. */
  private marcarLidas(pagina: PaginaNotificacoes, geracao: number): void {
    const ate = pagina.maisNovaEm;
    const haPorLer = this.servico.naoLidas() > 0 || pagina.itens.some((notificacao) => notificacao.nova);
    if (!ate || !haPorLer) {
      this.marcando.set(false);
      return;
    }
    this.servico.marcarLidas(ate).subscribe({
      next: () => {
        if (geracao === this.geracao) {
          this.marcando.set(false);
        }
      },
      // Sem a marcação, o contador continua como estava, e a próxima visita tenta de novo.
      error: () => {
        if (geracao === this.geracao) {
          this.marcando.set(false);
        }
      },
    });
  }

  private lembrarNovas(pagina: PaginaNotificacoes): void {
    const novas = pagina.itens.filter((notificacao) => notificacao.nova).map((notificacao) => notificacao.id);
    if (novas.length) {
      this.novasDaVisita.update((atuais) => new Set([...atuais, ...novas]));
    }
  }

  /** Rola até o título, mas só se o começo da lista já saiu da tela. */
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
