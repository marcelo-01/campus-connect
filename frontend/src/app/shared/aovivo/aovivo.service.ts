import { DOCUMENT, Injectable, InjectionToken, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subject, filter, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { LeitorSse } from './leitor-sse';

/** O `fetch` do navegador. Os testes trocam por um falso. */
export const FETCH = new InjectionToken<typeof fetch>('fetch', {
  providedIn: 'root',
  factory: () => globalThis.fetch.bind(globalThis),
});

/** A espera antes de cada nova tentativa de conexão. Depois da última, repete a última. */
export const ESPERAS_PARA_RECONECTAR = [1_000, 2_000, 5_000, 10_000, 30_000];

/** Sem nenhum byte por esse tempo, a conexão é dada como morta. O servidor manda um pulso a cada 25 s. */
export const SILENCIO_MAXIMO = 65_000;

/** Uma aba escondida por esse tempo fecha a sua conexão, que volta quando a aba fica visível. */
export const ESCONDIDA_ANTES_DE_FECHAR = 60_000;

/** Eventos de controle da conexão; os outros vão para quem assina `eventos`. */
const PRONTO = 'pronto';
const ENCERRADA = 'encerrada';

interface EventoAoVivo {
  tipo: string;
  dados: unknown;
}

/**
 * A conexão ao vivo (SSE) com o servidor, por onde chegam mensagens novas, leituras e, depois, notificações.
 *
 * Fica aberta só enquanto a aba está visível. O navegador abre no máximo 6 conexões por site em HTTP/1.1, e uma SSE
 * em cada aba aberta impediria as outras abas de carregar. Uma aba escondida por mais de um minuto fecha a sua e
 * reabre quando volta.
 *
 * Um evento enviado com a conexão fechada se perde. Por isso, a cada conexão o servidor manda `pronto`, e quem usa
 * os eventos busca de novo pela API o que pode ter perdido (`sincronizar`).
 *
 * Abre por `fetch`, e não por `EventSource`, porque o `EventSource` não manda o JWT no cabeçalho.
 */
@Injectable({ providedIn: 'root' })
export class AoVivoService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly documento = inject(DOCUMENT);
  private readonly buscar = inject(FETCH);

  private readonly todos = new Subject<EventoAoVivo>();
  private readonly prontos = new Subject<void>();

  /** Dispara a cada conexão aberta, inclusive a primeira: é a hora de buscar o que pode ter sido perdido. */
  readonly sincronizar: Observable<void> = this.prontos.asObservable();

  private usuarioId: string | null = null;
  private controle: AbortController | null = null;
  private tentativas = 0;
  /** O servidor fechou esta conexão para abrir a de outra aba: só volta quando a aba ficar visível de novo. */
  private substituida = false;
  private reconexao: ReturnType<typeof setTimeout> | null = null;
  private vigia: ReturnType<typeof setTimeout> | null = null;
  private fechamento: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.documento.addEventListener('visibilitychange', () => this.aoMudarVisibilidade());
    this.documento.defaultView?.addEventListener('online', () => this.reconectarAgora());
  }

  /** Os dados dos eventos de um tipo, já convertidos do JSON. */
  eventos<T>(tipo: string): Observable<T> {
    return this.todos.pipe(
      filter((evento) => evento.tipo === tipo),
      map((evento) => evento.dados as T),
    );
  }

  /**
   * Liga a conexão para a conta, ou desliga com `null`. Trocar de conta fecha a conexão da anterior: senão, a conta
   * nova receberia os eventos da antiga.
   */
  conectarComo(usuarioId: string | null): void {
    if (usuarioId === this.usuarioId) {
      return;
    }
    this.desligar();
    this.usuarioId = usuarioId;
    this.tentativas = 0;
    this.substituida = false;
    void this.abrir();
  }

  // ------------------------------------------------------------ ciclo da conexão

  private visivel(): boolean {
    return this.documento.visibilityState === 'visible';
  }

  private aoMudarVisibilidade(): void {
    if (this.visivel()) {
      this.limpar('fechamento');
      this.substituida = false;
      this.reconectarAgora();
    } else if (this.controle && !this.fechamento) {
      this.fechamento = setTimeout(() => {
        this.fechamento = null;
        this.fecharConexao();
      }, ESCONDIDA_ANTES_DE_FECHAR);
    }
  }

  /** Abre já, sem esperar a próxima tentativa agendada. */
  private reconectarAgora(): void {
    if (this.usuarioId && !this.controle) {
      this.limpar('reconexao');
      this.tentativas = 0;
      void this.abrir();
    }
  }

  private async abrir(): Promise<void> {
    if (!this.usuarioId || this.controle || this.substituida || !this.visivel()) {
      return;
    }
    const controle = new AbortController();
    this.controle = controle;
    let reconectar = true;
    try {
      const resposta = await this.conectar(controle);
      if (!resposta) {
        reconectar = false;
        return;
      }
      await this.ler(resposta, controle);
    } catch {
      // Rede caída, servidor fora do ar ou conexão abortada: a decisão de tentar de novo fica abaixo.
    } finally {
      this.limpar('vigia');
      // Se a conexão foi desligada ou trocada no meio do caminho, quem fez isso já cuidou do resto.
      if (this.controle === controle) {
        this.controle = null;
        if (reconectar && !this.substituida) {
          this.agendarReconexao();
        }
      }
    }
  }

  /** Faz a requisição. No 401, renova a sessão e tenta mais uma vez; sem sessão, volta para o login. */
  private async conectar(controle: AbortController): Promise<Response | null> {
    let resposta = await this.pedir(controle);
    if (resposta.status === 401) {
      const renovou = await this.auth.renovar();
      if (!renovou) {
        void this.router.navigateByUrl('/entrar');
        return null;
      }
      resposta = await this.pedir(controle);
    }
    const tipo = resposta.headers.get('content-type') ?? '';
    if (!resposta.ok || !resposta.body || !tipo.startsWith('text/event-stream')) {
      throw new Error(`A conexão ao vivo respondeu ${resposta.status}`);
    }
    return resposta;
  }

  private pedir(controle: AbortController): Promise<Response> {
    const token = this.auth.accessToken;
    return this.buscar('/api/aovivo', {
      headers: token
        ? { Accept: 'text/event-stream', Authorization: `Bearer ${token}` }
        : { Accept: 'text/event-stream' },
      cache: 'no-store',
      signal: controle.signal,
    });
  }

  private async ler(resposta: Response, controle: AbortController): Promise<void> {
    const leitor = resposta.body!.getReader();
    const sse = new LeitorSse();
    this.vigiar(controle);
    for (;;) {
      const { value, done } = await leitor.read();
      if (done) {
        return;
      }
      this.vigiar(controle);
      for (const evento of sse.ler(value)) {
        this.receber(evento.tipo, evento.dados, controle);
      }
      if (controle.signal.aborted) {
        return;
      }
    }
  }

  private receber(tipo: string, texto: string, controle: AbortController): void {
    if (tipo === PRONTO) {
      this.tentativas = 0;
      this.prontos.next();
      return;
    }
    if (tipo === ENCERRADA) {
      this.substituida = true;
      controle.abort();
      return;
    }
    let dados: unknown;
    try {
      dados = JSON.parse(texto);
    } catch {
      return;
    }
    this.todos.next({ tipo, dados });
  }

  /** Reinicia o vigia a cada byte que chega: uma conexão meio morta (notebook que dormiu) não dá erro sozinha. */
  private vigiar(controle: AbortController): void {
    this.limpar('vigia');
    this.vigia = setTimeout(() => controle.abort(), SILENCIO_MAXIMO);
  }

  private agendarReconexao(): void {
    if (!this.usuarioId) {
      return;
    }
    const base = ESPERAS_PARA_RECONECTAR[Math.min(this.tentativas, ESPERAS_PARA_RECONECTAR.length - 1)];
    this.tentativas++;
    // Uma variação aleatória evita que todas as abas reconectem no mesmo instante depois de uma queda do servidor.
    const espera = base * (0.7 + Math.random() * 0.6);
    this.limpar('reconexao');
    this.reconexao = setTimeout(() => {
      this.reconexao = null;
      void this.abrir();
    }, espera);
  }

  /** Fecha a conexão sem agendar outra. A conta continua a mesma. */
  private fecharConexao(): void {
    const controle = this.controle;
    this.controle = null;
    this.limpar('vigia');
    this.limpar('reconexao');
    controle?.abort();
  }

  private desligar(): void {
    this.fecharConexao();
    this.limpar('fechamento');
    this.usuarioId = null;
  }

  private limpar(temporizador: 'reconexao' | 'vigia' | 'fechamento'): void {
    const atual = this[temporizador];
    if (atual) {
      clearTimeout(atual);
      this[temporizador] = null;
    }
  }
}
