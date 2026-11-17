import { ChangeDetectionStrategy, Component, DOCUMENT, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Observable, Subject, catchError, debounceTime, merge, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { AoVivoService } from '../../shared/aovivo/aovivo.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { dataCompleta, tempoRelativo } from '../../shared/tempo-relativo';
import {
  EVENTO_LEITURA,
  EVENTO_MENSAGEM,
  EVENTO_MENSAGEM_APAGADA,
  EventoLeitura,
  EventoMensagemApagada,
  Mensagem,
  MensagemService,
  Pagina,
  ResumoConversa,
  cortarPrevia,
  previa,
} from '../mensagem.service';
import { PainelMensagens } from '../painel-mensagens';

/**
 * O `MessagesScreen` do MVP: a lista de conversas à esquerda e a conversa aberta à direita (rota filha).
 *
 * Do MVP saíram o "● online" (não há presença), os chips "Bolhas" e "Marketplace" (não há dado para eles) e o botão
 * de emoji, que não fazia nada. A busca procura no nome e no username da outra pessoa, no servidor.
 */
@Component({
  selector: 'app-mensagens-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterOutlet, Avatar, Icone],
  providers: [PainelMensagens],
  styles: ':host { display: contents; }',
  templateUrl: './mensagens-pagina.html',
})
export class MensagensPagina {
  private readonly servico = inject(MensagemService);
  private readonly auth = inject(AuthService);
  private readonly aoVivo = inject(AoVivoService);
  private readonly documento = inject(DOCUMENT);
  protected readonly painel = inject(PainelMensagens);

  /** `null` enquanto a primeira página não chegou. */
  protected readonly conversas = signal<ResumoConversa[] | null>(null);
  protected readonly proximo = signal<string | null>(null);
  protected readonly erro = signal(false);
  protected readonly carregandoMais = signal(false);
  protected readonly busca = signal('');
  protected readonly soNaoLidas = signal(false);

  protected readonly previa = previa;
  protected readonly tempoRelativo = tempoRelativo;
  protected readonly dataCompleta = dataCompleta;

  private readonly digitacao = new Subject<void>();
  private readonly recargas = new Subject<void>();

  constructor() {
    merge(this.digitacao.pipe(debounceTime(300)), this.recargas, this.aoVivo.sincronizar)
      .pipe(
        startWith(null),
        switchMap(() => this.primeiraPagina()),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        if (pagina) {
          this.erro.set(false);
          this.conversas.set(pagina.itens);
          this.proximo.set(pagina.proximo);
        } else if (this.conversas() === null) {
          this.erro.set(true);
        }
      });

    merge(this.aoVivo.eventos<Mensagem>(EVENTO_MENSAGEM), this.painel.enviada)
      .pipe(takeUntilDestroyed())
      .subscribe((mensagem) => this.aoChegarMensagem(mensagem));
    this.aoVivo
      .eventos<EventoLeitura>(EVENTO_LEITURA)
      .pipe(takeUntilDestroyed())
      .subscribe((leitura) => {
        if (leitura.leitorId === this.auth.usuario()?.id) {
          this.marcarComoLida(leitura.conversaId);
        }
      });
    this.painel.lida.pipe(takeUntilDestroyed()).subscribe((conversaId) => this.marcarComoLida(conversaId));
    this.aoVivo
      .eventos<EventoMensagemApagada>(EVENTO_MENSAGEM_APAGADA)
      .pipe(takeUntilDestroyed())
      .subscribe((apagada) => this.aoApagarMensagem(apagada));
  }

  protected digitar(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
    this.digitacao.next();
  }

  protected filtrar(soNaoLidas: boolean): void {
    if (soNaoLidas !== this.soNaoLidas()) {
      this.soNaoLidas.set(soNaoLidas);
      this.recargas.next();
    }
  }

  protected tentarDeNovo(): void {
    this.erro.set(false);
    this.recargas.next();
  }

  protected carregarMais(): void {
    const cursor = this.proximo();
    if (!cursor || this.carregandoMais()) {
      return;
    }
    this.carregandoMais.set(true);
    this.servico.listar({ cursor, busca: this.busca(), soNaoLidas: this.soNaoLidas() }).subscribe({
      next: (pagina) => {
        const vistas = new Set((this.conversas() ?? []).map((conversa) => conversa.id));
        this.conversas.update((atuais) => [
          ...(atuais ?? []),
          ...pagina.itens.filter((conversa) => !vistas.has(conversa.id)),
        ]);
        this.proximo.set(pagina.proximo);
        this.carregandoMais.set(false);
      },
      error: () => this.carregandoMais.set(false),
    });
  }

  private primeiraPagina(): Observable<Pagina<ResumoConversa> | null> {
    return this.servico
      .listar({ busca: this.busca(), soNaoLidas: this.soNaoLidas() })
      .pipe(catchError(() => of(null)));
  }

  /** A conversa sobe para o topo com a mensagem nova. Uma conversa que não estava na lista faz a lista recarregar. */
  private aoChegarMensagem(mensagem: Mensagem): void {
    const lista = this.conversas();
    if (!lista) {
      return;
    }
    const atual = lista.find((conversa) => conversa.id === mensagem.conversaId);
    if (!atual) {
      this.recargas.next();
      return;
    }
    const minha = mensagem.remetenteId === this.auth.usuario()?.id;
    const lendoAgora =
      this.painel.ativa() === mensagem.conversaId && this.documento.visibilityState === 'visible';
    const atualizada: ResumoConversa = {
      ...atual,
      ultima: {
        mensagemId: mensagem.id,
        texto: mensagem.conteudo === null ? null : cortarPrevia(mensagem.conteudo),
        minha,
        enviadaEm: mensagem.enviadoEm,
        apagada: mensagem.apagada,
      },
      naoLida: atual.naoLida || (!minha && !lendoAgora),
    };
    this.conversas.set([atualizada, ...lista.filter((conversa) => conversa.id !== atual.id)]);
  }

  private marcarComoLida(conversaId: string): void {
    this.conversas.update((lista) =>
      lista?.map((conversa) => (conversa.id === conversaId ? { ...conversa, naoLida: false } : conversa)) ?? null,
    );
  }

  private aoApagarMensagem(apagada: EventoMensagemApagada): void {
    this.conversas.update(
      (lista) =>
        lista?.map((conversa) =>
          conversa.ultima.mensagemId === apagada.mensagemId
            ? { ...conversa, ultima: { ...conversa.ultima, texto: null, apagada: true } }
            : conversa,
        ) ?? null,
    );
  }
}

/** A coluna da direita sem conversa aberta. */
@Component({
  selector: 'app-sem-conversa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  styles: ':host { display: contents; }',
  template: `
    <div class="thread-view thread-vazia">
      <div class="empty-bubble"><app-icone nome="mensagens" /></div>
      <h3>Escolha uma conversa</h3>
      <p>As mensagens são privadas: só você e a outra pessoa veem.</p>
    </div>
  `,
})
export class SemConversa {
  constructor() {
    inject(PainelMensagens).ativa.set(null);
  }
}
