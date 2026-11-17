import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, distinctUntilChanged, filter, forkJoin, map, merge, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { ConexaoService, primeiroNome, recadoDaAcao } from '../../conexoes/conexao.service';
import { MenuPessoa } from '../../conexoes/menu-pessoa';
import { TrilhaService } from '../../layout/trilha.service';
import { AoVivoService } from '../../shared/aovivo/aovivo.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { dataCompleta } from '../../shared/tempo-relativo';
import { ToastService } from '../../shared/toast.service';
import { EstadoAberturaConversa } from '../botao-mensagem';
import {
  Conversa,
  EVENTO_LEITURA,
  EVENTO_MENSAGEM,
  EVENTO_MENSAGEM_APAGADA,
  EventoLeitura,
  EventoMensagemApagada,
  Mensagem,
  MensagemService,
  Pagina,
  TAMANHO_MAXIMO_MENSAGEM,
  comoApagada,
  compararInstantes,
  mesclar,
  separarPorDia,
  tamanhoDaMensagem,
} from '../mensagem.service';
import { PainelMensagens } from '../painel-mensagens';

/** Uma mensagem que ainda não voltou do servidor. */
interface Pendente {
  chave: number;
  conteudo: string;
  falhou: boolean;
}

/** A partir de quantos caracteres o campo mostra a contagem. */
const AVISO_DE_TAMANHO = 1800;

/** Quão perto do fim a conversa precisa estar para acompanhar as mensagens novas. */
const PERTO_DO_FIM_PX = 80;

/**
 * A conversa aberta, na coluna da direita da página de Mensagens.
 *
 * A mensagem enviada aparece na hora, meio apagada, e fica firme quando o servidor confirma. A conversa é marcada
 * como lida quando abre e quando chega mensagem com ela na tela; com a aba escondida, espera a pessoa voltar.
 */
@Component({
  selector: 'app-conversa-aberta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone, MenuPessoa],
  styles: ':host { display: contents; }',
  host: { '(document:visibilitychange)': 'marcarLida()' },
  templateUrl: './conversa-aberta.html',
})
export class ConversaAberta {
  private readonly servico = inject(MensagemService);
  private readonly auth = inject(AuthService);
  private readonly aoVivo = inject(AoVivoService);
  private readonly conexoes = inject(ConexaoService);
  private readonly toast = inject(ToastService);
  private readonly trilhas = inject(TrilhaService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly painel = inject(PainelMensagens);

  private readonly corpo = viewChild<ElementRef<HTMLElement>>('corpo');
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  private readonly id = toSignal(this.rota.paramMap.pipe(map((parametros) => parametros.get('id') ?? '')), {
    requireSync: true,
  });

  protected readonly conversa = signal<Conversa | null>(null);
  protected readonly naoEncontrada = signal(false);
  protected readonly erro = signal(false);
  protected readonly mensagens = signal<Mensagem[]>([]);
  protected readonly proximo = signal<string | null>(null);
  protected readonly carregandoAnteriores = signal(false);
  protected readonly pendentes = signal<Pendente[]>([]);
  protected readonly texto = signal('');
  protected readonly confirmandoApagar = signal<string | null>(null);

  protected readonly eu = computed(() => this.auth.usuario()?.id ?? '');
  protected readonly grupos = computed(() => separarPorDia(this.mensagens()));
  protected readonly temHoje = computed(() => this.grupos().at(-1)?.dia === 'Hoje');
  protected readonly nome = computed(() => primeiroNome(this.conversa()?.pessoa.nomeCompleto ?? ''));
  protected readonly tamanho = computed(() => tamanhoDaMensagem(this.texto()));
  protected readonly podeMandar = computed(
    () => this.tamanho() > 0 && this.tamanho() <= TAMANHO_MAXIMO_MENSAGEM && !!this.conversa()?.podeEnviar,
  );
  /** "Visto" debaixo da minha última mensagem, quando ela é a última da conversa e a outra pessoa já leu. */
  protected readonly visto = computed(() => {
    const leuEm = this.conversa()?.outroLeuEm;
    const ultima = this.mensagens().at(-1);
    return (
      !!leuEm &&
      !!ultima &&
      ultima.remetenteId === this.eu() &&
      !ultima.apagada &&
      this.pendentes().length === 0 &&
      compararInstantes(leuEm, ultima.enviadoEm) >= 0
    );
  });

  protected readonly maximo = TAMANHO_MAXIMO_MENSAGEM;
  protected readonly avisoDeTamanho = AVISO_DE_TAMANHO;
  protected readonly dataCompleta = dataCompleta;

  private readonly recargas = new Subject<void>();
  private carregada = false;
  private leituraPedida: string | null = null;
  private proximaChave = 0;

  constructor() {
    const trocas = this.rota.paramMap.pipe(
      map((parametros) => parametros.get('id') ?? ''),
      distinctUntilChanged(),
      tap((id) => this.preparar(id)),
    );
    const atualizacoes = merge(this.recargas, this.aoVivo.sincronizar.pipe(filter(() => this.carregada))).pipe(
      map(() => this.id()),
    );
    merge(trocas, atualizacoes)
      .pipe(
        switchMap((id) => this.carregar(id)),
        takeUntilDestroyed(),
      )
      .subscribe(({ conversa, pagina }) => this.aoCarregar(conversa, pagina));

    this.aoVivo
      .eventos<Mensagem>(EVENTO_MENSAGEM)
      .pipe(takeUntilDestroyed())
      .subscribe((mensagem) => this.aoChegarMensagem(mensagem));
    this.aoVivo
      .eventos<EventoLeitura>(EVENTO_LEITURA)
      .pipe(takeUntilDestroyed())
      .subscribe((leitura) => {
        if (leitura.conversaId === this.id() && leitura.leitorId !== this.eu()) {
          this.conversa.update((conversa) => conversa && { ...conversa, outroLeuEm: leitura.ate });
        }
      });
    this.aoVivo
      .eventos<EventoMensagemApagada>(EVENTO_MENSAGEM_APAGADA)
      .pipe(takeUntilDestroyed())
      .subscribe((apagada) => {
        if (apagada.conversaId === this.id()) {
          this.mensagens.update((lista) =>
            lista.map((mensagem) => (mensagem.id === apagada.mensagemId ? comoApagada(mensagem) : mensagem)),
          );
        }
      });

    inject(DestroyRef).onDestroy(() => {
      if (this.painel.ativa() === this.id()) {
        this.painel.ativa.set(null);
      }
    });
  }

  // ------------------------------------------------------------ envio

  protected enviar(evento: Event): void {
    evento.preventDefault();
    if (!this.podeMandar()) {
      return;
    }
    const pendente: Pendente = { chave: ++this.proximaChave, conteudo: this.texto().trim(), falhou: false };
    this.texto.set('');
    this.pendentes.update((lista) => [...lista, pendente]);
    this.rolarParaOFim();
    this.postar(pendente);
  }

  protected tentarDeNovo(pendente: Pendente): void {
    this.pendentes.update((lista) =>
      lista.map((item) => (item.chave === pendente.chave ? { ...item, falhou: false } : item)),
    );
    this.postar(pendente);
  }

  protected descartar(pendente: Pendente): void {
    this.tirarPendente(pendente.chave);
  }

  private postar(pendente: Pendente): void {
    const id = this.id();
    this.servico.enviar(id, pendente.conteudo).subscribe({
      next: (mensagem) => {
        this.tirarPendente(pendente.chave);
        if (this.id() === id) {
          this.mensagens.update((lista) => mesclar(lista, [mensagem]));
        }
        this.painel.enviada.next(mensagem);
      },
      error: (erro: unknown) => {
        const resposta = erro instanceof HttpErrorResponse ? erro : null;
        const detalhe = resposta?.error?.detail as string | undefined;
        if (resposta?.status === 403 || resposta?.status === 404) {
          // Bloqueio ou conta que sumiu: a tela se acerta com o servidor.
          this.tirarPendente(pendente.chave);
          this.toast.mostrar(detalhe ?? 'Não foi possível enviar a mensagem.');
          this.recargas.next();
          return;
        }
        this.pendentes.update((lista) =>
          lista.map((item) => (item.chave === pendente.chave ? { ...item, falhou: true } : item)),
        );
        if (detalhe) {
          this.toast.mostrar(detalhe);
        }
      },
    });
  }

  private tirarPendente(chave: number): void {
    this.pendentes.update((lista) => lista.filter((item) => item.chave !== chave));
  }

  // ------------------------------------------------------------ apagar, anteriores e bloqueio

  protected apagar(mensagem: Mensagem): void {
    this.confirmandoApagar.set(null);
    const id = this.id();
    this.servico.apagar(id, mensagem.id).subscribe({
      next: () => {
        if (this.id() === id) {
          this.mensagens.update((lista) =>
            lista.map((item) => (item.id === mensagem.id ? comoApagada(item) : item)),
          );
        }
      },
      error: () => this.toast.mostrar('Não foi possível apagar a mensagem. Tente de novo.'),
    });
  }

  /** Busca a página anterior sem a tela pular: a mensagem que estava no topo continua no mesmo lugar. */
  protected carregarAnteriores(): void {
    const cursor = this.proximo();
    const id = this.id();
    if (!cursor || this.carregandoAnteriores()) {
      return;
    }
    this.carregandoAnteriores.set(true);
    this.servico.mensagens(id, cursor).subscribe({
      next: (pagina) => {
        if (this.id() !== id) {
          return;
        }
        const corpo = this.corpo()?.nativeElement;
        const distanciaDoFim = corpo ? corpo.scrollHeight - corpo.scrollTop : 0;
        this.mensagens.update((lista) => mesclar(lista, pagina.itens));
        this.proximo.set(pagina.proximo);
        this.carregandoAnteriores.set(false);
        afterNextRender(
          () => {
            if (corpo) {
              corpo.scrollTop = corpo.scrollHeight - distanciaDoFim;
            }
          },
          { injector: this.injector },
        );
      },
      error: () => {
        this.carregandoAnteriores.set(false);
        this.toast.mostrar('Não foi possível carregar as mensagens anteriores.');
      },
    });
  }

  /** Depois de bloquear ou desbloquear pelo menu, a conversa se acerta com o servidor. */
  protected aoMudarBloqueio(): void {
    this.recargas.next();
  }

  protected desbloquear(): void {
    const conversa = this.conversa();
    if (!conversa) {
      return;
    }
    const nome = this.nome();
    this.conexoes.desbloquear(conversa.pessoa.id).subscribe({
      next: (situacao) => {
        this.toast.mostrar(recadoDaAcao('desbloquear', situacao, nome));
        this.recargas.next();
      },
      error: () => this.toast.mostrar('Não foi possível desbloquear. Tente de novo.'),
    });
  }

  protected tentarCarregar(): void {
    this.erro.set(false);
    this.recargas.next();
  }

  // ------------------------------------------------------------ leitura

  /** Marca como lida até a última mensagem, se ela é da outra pessoa e a aba está visível. */
  protected marcarLida(): void {
    const id = this.id();
    const ultima = this.mensagens().at(-1);
    if (
      !ultima ||
      ultima.remetenteId === this.eu() ||
      this.documento.visibilityState !== 'visible' ||
      this.leituraPedida === ultima.id
    ) {
      return;
    }
    this.leituraPedida = ultima.id;
    this.servico.lerAte(id, ultima.id).subscribe({
      next: () => {
        this.painel.lida.next(id);
        this.servico.recarregarNaoLidas();
      },
      error: () => {
        if (this.leituraPedida === ultima.id) {
          this.leituraPedida = null;
        }
      },
    });
  }

  // ------------------------------------------------------------ carga

  /** Antes de carregar outra conversa: limpa a anterior e pega o texto inicial que veio na navegação. */
  private preparar(id: string): void {
    this.carregada = false;
    this.leituraPedida = null;
    this.conversa.set(null);
    this.naoEncontrada.set(false);
    this.erro.set(false);
    this.mensagens.set([]);
    this.proximo.set(null);
    this.pendentes.set([]);
    this.confirmandoApagar.set(null);
    this.painel.ativa.set(id);
    // Vindo de outra página, esta conversa é ativada pelo <router-outlet> da página de Mensagens só depois que a
    // navegação termina: aí a navegação que a trouxe já é a última concluída. Trocando de conversa dentro da página,
    // ela ainda é a atual.
    const navegacao = this.router.currentNavigation() ?? this.router.lastSuccessfulNavigation();
    const estado = navegacao?.extras.state as EstadoAberturaConversa | undefined;
    this.texto.set(estado?.rascunho ?? '');
  }

  private carregar(id: string): Observable<{ conversa: Conversa; pagina: Pagina<Mensagem> }> {
    return forkJoin({ conversa: this.servico.buscar(id), pagina: this.servico.mensagens(id) }).pipe(
      catchError((erro: unknown) => {
        if (!this.carregada) {
          if (erro instanceof HttpErrorResponse && erro.status === 404) {
            this.naoEncontrada.set(true);
          } else {
            this.erro.set(true);
          }
        }
        return EMPTY;
      }),
    );
  }

  /**
   * Na primeira carga, a conversa abre no fim. Nas seguintes (depois de reconectar ou de um bloqueio), as mensagens
   * da primeira página se juntam às que já estavam, com a versão do servidor valendo.
   */
  private aoCarregar(conversa: Conversa, pagina: Pagina<Mensagem>): void {
    const primeira = !this.carregada;
    const estavaNoFim = this.estaNoFim();
    this.carregada = true;
    this.conversa.set(conversa);
    this.trilhas.definir(['Mensagens', conversa.pessoa.nomeCompleto]);
    if (primeira) {
      this.mensagens.set(mesclar([], pagina.itens));
      this.proximo.set(pagina.proximo);
      this.rolarParaOFim();
      afterNextRender(() => this.campo()?.nativeElement.focus(), { injector: this.injector });
    } else {
      this.mensagens.update((lista) => mesclar(lista, pagina.itens));
      if (estavaNoFim) {
        this.rolarParaOFim();
      }
    }
    this.marcarLida();
  }

  private aoChegarMensagem(mensagem: Mensagem): void {
    if (mensagem.conversaId !== this.id() || !this.carregada) {
      return;
    }
    const estavaNoFim = this.estaNoFim();
    if (mensagem.remetenteId === this.eu()) {
      // A conexão ao vivo costuma chegar antes da resposta do envio: a mensagem pendente dá lugar à confirmada.
      const pendente = this.pendentes().find((item) => !item.falhou && item.conteudo === mensagem.conteudo);
      if (pendente) {
        this.tirarPendente(pendente.chave);
      }
    }
    this.mensagens.update((lista) => mesclar(lista, [mensagem]));
    if (estavaNoFim || mensagem.remetenteId === this.eu()) {
      this.rolarParaOFim();
    }
    this.marcarLida();
  }

  private estaNoFim(): boolean {
    const corpo = this.corpo()?.nativeElement;
    return !corpo || corpo.scrollHeight - corpo.scrollTop - corpo.clientHeight < PERTO_DO_FIM_PX;
  }

  private rolarParaOFim(): void {
    afterNextRender(
      () => {
        const corpo = this.corpo()?.nativeElement;
        if (corpo) {
          corpo.scrollTop = corpo.scrollHeight;
        }
      },
      { injector: this.injector },
    );
  }
}
