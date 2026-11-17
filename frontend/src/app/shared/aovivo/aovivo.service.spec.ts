import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { AoVivoService, ESCONDIDA_ANTES_DE_FECHAR, FETCH, SILENCIO_MAXIMO } from './aovivo.service';

/** O corpo de uma resposta que o teste vai preenchendo, como um servidor mandando eventos aos poucos. */
class FluxoFalso {
  private readonly prontos: { value?: Uint8Array; done: boolean }[] = [];
  private esperando: ((pedaco: { value?: Uint8Array; done: boolean }) => void) | null = null;
  private falhar: ((erro: unknown) => void) | null = null;
  abortado = false;

  enviar(texto: string): void {
    this.entregar({ value: new TextEncoder().encode(texto), done: false });
  }

  fechar(): void {
    this.entregar({ done: true });
  }

  abortar(): void {
    this.abortado = true;
    this.falhar?.(new DOMException('A conexão foi abortada.', 'AbortError'));
  }

  read(): Promise<{ value?: Uint8Array; done: boolean }> {
    // Como no fetch de verdade: depois de abortada, a leitura falha.
    if (this.abortado) {
      return Promise.reject(new DOMException('A conexão foi abortada.', 'AbortError'));
    }
    const pronto = this.prontos.shift();
    if (pronto) {
      return Promise.resolve(pronto);
    }
    return new Promise((resolver, rejeitar) => {
      this.esperando = resolver;
      this.falhar = rejeitar;
    });
  }

  private entregar(pedaco: { value?: Uint8Array; done: boolean }): void {
    const esperando = this.esperando;
    this.esperando = null;
    if (esperando) {
      esperando(pedaco);
    } else {
      this.prontos.push(pedaco);
    }
  }
}

interface Conexao {
  init: RequestInit;
  fluxo: FluxoFalso;
}

describe('AoVivoService', () => {
  let visibilidade: DocumentVisibilityState;
  let conexoes: Conexao[];
  let respostas: number[];
  let auth: { accessToken: string | null; renovar: ReturnType<typeof vi.fn<() => Promise<boolean>>> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };
  let servico: AoVivoService;

  function buscarFalso(_url: string, init: RequestInit): Promise<unknown> {
    const fluxo = new FluxoFalso();
    init.signal?.addEventListener('abort', () => fluxo.abortar());
    conexoes.push({ init, fluxo });
    const status = respostas.shift() ?? 200;
    return Promise.resolve({
      status,
      ok: status >= 200 && status < 300,
      headers: { get: () => 'text/event-stream;charset=UTF-8' },
      body: { getReader: () => fluxo },
    });
  }

  function mudarVisibilidade(nova: DocumentVisibilityState): void {
    visibilidade = nova;
    document.dispatchEvent(new Event('visibilitychange'));
  }

  const ultima = () => conexoes.at(-1)!;
  const esperar = (ms = 0) => vi.advanceTimersByTimeAsync(ms);

  beforeEach(() => {
    vi.useFakeTimers();
    // Sem variação aleatória: as esperas ficam exatas.
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    visibilidade = 'visible';
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibilidade });
    conexoes = [];
    respostas = [];
    auth = { accessToken: 'jwt-1', renovar: vi.fn<() => Promise<boolean>>() };
    router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: FETCH, useValue: buscarFalso },
      ],
    });
    servico = TestBed.inject(AoVivoService);
  });

  afterEach(() => {
    servico.conectarComo(null);
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('abre com o JWT, repassa os eventos e pede para sincronizar a cada conexão', async () => {
    const sincronizacoes: number[] = [];
    const mensagens: unknown[] = [];
    servico.sincronizar.subscribe(() => sincronizacoes.push(1));
    servico.eventos('mensagem').subscribe((dados) => mensagens.push(dados));

    servico.conectarComo('ana');
    await esperar();

    expect(conexoes).toHaveLength(1);
    expect(ultima().init.headers).toEqual({ Accept: 'text/event-stream', Authorization: 'Bearer jwt-1' });

    ultima().fluxo.enviar('event:pronto\ndata:{}\n\nevent:mensagem\ndata:{"id":"m1"}\n\nevent:mensagem\ndata:nao-e-json\n\n');
    await esperar();

    expect(sincronizacoes).toHaveLength(1);
    expect(mensagens).toEqual([{ id: 'm1' }]);
  });

  it('reconecta com espera crescente e volta ao começo depois de conectar', async () => {
    servico.conectarComo('ana');
    await esperar();

    ultima().fluxo.fechar();
    await esperar(999);
    expect(conexoes).toHaveLength(1);
    await esperar(1);
    expect(conexoes).toHaveLength(2);

    // A segunda cai antes do "pronto": a espera dobra.
    ultima().fluxo.fechar();
    await esperar(1999);
    expect(conexoes).toHaveLength(2);
    await esperar(1);
    expect(conexoes).toHaveLength(3);

    // Com o "pronto", a próxima queda volta a esperar 1 s.
    ultima().fluxo.enviar('event:pronto\ndata:{}\n\n');
    await esperar();
    ultima().fluxo.fechar();
    await esperar(1000);
    expect(conexoes).toHaveLength(4);
  });

  it('no 401 renova a sessão e tenta de novo com o JWT novo', async () => {
    respostas = [401, 200];
    auth.renovar.mockImplementation(async () => {
      auth.accessToken = 'jwt-2';
      return true;
    });

    servico.conectarComo('ana');
    await esperar();

    expect(auth.renovar).toHaveBeenCalledTimes(1);
    expect(conexoes).toHaveLength(2);
    expect(ultima().init.headers).toEqual({ Accept: 'text/event-stream', Authorization: 'Bearer jwt-2' });
  });

  it('sem sessão para de tentar e volta para o login', async () => {
    respostas = [401];
    auth.renovar.mockResolvedValue(false);

    servico.conectarComo('ana');
    await esperar(60_000);

    expect(conexoes).toHaveLength(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/entrar');
  });

  it('a aba escondida por um minuto fecha a conexão, que volta quando a aba aparece', async () => {
    const sincronizacoes: number[] = [];
    servico.sincronizar.subscribe(() => sincronizacoes.push(1));
    servico.conectarComo('ana');
    await esperar();

    mudarVisibilidade('hidden');
    await esperar(ESCONDIDA_ANTES_DE_FECHAR - 1);
    expect(ultima().fluxo.abortado).toBe(false);
    await esperar(1);
    expect(ultima().fluxo.abortado).toBe(true);

    await esperar(120_000);
    expect(conexoes).toHaveLength(1);

    mudarVisibilidade('visible');
    await esperar();
    expect(conexoes).toHaveLength(2);
    ultima().fluxo.enviar('event:pronto\ndata:{}\n\n');
    await esperar();
    expect(sincronizacoes).toHaveLength(1);
  });

  it('esconder a aba por pouco tempo não fecha nada', async () => {
    servico.conectarComo('ana');
    await esperar();

    mudarVisibilidade('hidden');
    await esperar(30_000);
    ultima().fluxo.enviar(': pulso\n\n');
    mudarVisibilidade('visible');
    await esperar(60_000);

    expect(conexoes).toHaveLength(1);
    expect(ultima().fluxo.abortado).toBe(false);
  });

  it('reconecta quando a conexão fica muda, como depois de o computador dormir', async () => {
    servico.conectarComo('ana');
    await esperar();
    ultima().fluxo.enviar(': pulso\n\n');

    await esperar(SILENCIO_MAXIMO);
    expect(conexoes[0].fluxo.abortado).toBe(true);
    await esperar(1000);
    expect(conexoes).toHaveLength(2);
  });

  it('trocar de conta fecha a conexão da anterior', async () => {
    servico.conectarComo('ana');
    await esperar();

    servico.conectarComo('bia');
    await esperar();

    expect(conexoes[0].fluxo.abortado).toBe(true);
    expect(conexoes).toHaveLength(2);

    servico.conectarComo(null);
    await esperar(60_000);
    expect(conexoes[1].fluxo.abortado).toBe(true);
    expect(conexoes).toHaveLength(2);
  });

  it('substituída por outra aba, só volta quando a aba aparece de novo', async () => {
    servico.conectarComo('ana');
    await esperar();

    ultima().fluxo.enviar('event:encerrada\ndata:{"motivo":"substituida"}\n\n');
    await esperar(120_000);
    expect(conexoes).toHaveLength(1);

    mudarVisibilidade('hidden');
    mudarVisibilidade('visible');
    await esperar();
    expect(conexoes).toHaveLength(2);
  });
});
