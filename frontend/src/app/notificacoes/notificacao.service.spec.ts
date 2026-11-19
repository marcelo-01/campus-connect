import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, filter, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioResumo } from '../auth/sessao';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { AoVivoService } from '../shared/aovivo/aovivo.service';
import {
  Notificacao,
  NotificacaoService,
  TipoNotificacao,
  acao,
  autores,
  destino,
  quemFez,
} from './notificacao.service';

function pessoa(nome: string): PessoaPublica {
  const username = nome.toLowerCase();
  return { id: username, username, nomeCompleto: nome, fotoUrl: null };
}

function notificacao(tipo: TipoNotificacao, nomes: string[], total = nomes.length): Notificacao {
  return {
    id: 'n1',
    tipo,
    em: '2026-11-19T12:00:00Z',
    nova: true,
    pessoas: nomes.map(pessoa),
    total,
    publicacaoId: 'p1',
    anuncioId: 'a1',
    trecho: null,
  };
}

/** O texto como aparece na tela, sem o negrito. */
function texto(n: Notificacao): string {
  return `${quemFez(autores(n))
    .map((parte) => parte.texto)
    .join('')} ${acao(n)}`;
}

describe('funções de notificações', () => {
  it('junta até dois nomes e conta as outras pessoas', () => {
    expect(texto(notificacao('CURTIDA_PUBLICACAO', ['Ana']))).toBe('Ana curtiu sua publicação');
    expect(texto(notificacao('CURTIDA_PUBLICACAO', ['Ana', 'Bia']))).toBe('Ana e Bia curtiram sua publicação');
    expect(texto(notificacao('CURTIDA_PUBLICACAO', ['Ana', 'Bia', 'Caio']))).toBe(
      'Ana, Bia e mais 1 pessoa curtiram sua publicação',
    );
    expect(texto(notificacao('REPUBLICACAO', ['Ana', 'Bia', 'Caio'], 5))).toBe(
      'Ana, Bia e mais 3 pessoas republicaram sua publicação',
    );
    // Quem não aparece mais para os outros entra só na conta.
    expect(texto(notificacao('CURTIDA_COMENTARIO', ['Ana'], 2))).toBe('Ana e mais 1 pessoa curtiram seu comentário');
  });

  it('põe só os nomes em negrito', () => {
    expect(quemFez(autores(notificacao('CURTIDA_PUBLICACAO', ['Ana', 'Bia'], 4)))).toEqual([
      { texto: 'Ana', nome: true },
      { texto: ', ', nome: false },
      { texto: 'Bia', nome: true },
      { texto: ' e ', nome: false },
      { texto: 'mais 2 pessoas', nome: false },
    ]);
  });

  it('descreve cada tipo', () => {
    expect(texto(notificacao('SOLICITACAO_CONEXAO', ['Ana']))).toBe('Ana quer se conectar com você');
    expect(texto(notificacao('CONEXAO_ACEITA', ['Ana']))).toBe('Ana aceitou seu pedido de conexão');
    expect(texto(notificacao('NOVO_COMENTARIO', ['Ana']))).toBe('Ana comentou na sua publicação');
    expect(texto(notificacao('RESPOSTA_COMENTARIO', ['Ana']))).toBe('Ana respondeu ao seu comentário');
    expect(texto(notificacao('AVALIACAO_ANUNCIO', ['Ana']))).toBe('Ana avaliou seu anúncio');
  });

  it('leva cada tipo para a sua tela', () => {
    expect(destino(notificacao('SOLICITACAO_CONEXAO', ['Ana']))).toEqual({
      link: ['/conexoes'],
      query: { aba: 'recebidos' },
    });
    expect(destino(notificacao('CONEXAO_ACEITA', ['Ana']))).toEqual({ link: ['/perfil', 'ana'] });
    expect(destino(notificacao('NOVO_COMENTARIO', ['Ana']))).toEqual({ link: ['/publicacoes', 'p1'] });
    expect(destino(notificacao('CURTIDA_COMENTARIO', ['Ana']))).toEqual({ link: ['/publicacoes', 'p1'] });
    expect(destino(notificacao('AVALIACAO_ANUNCIO', ['Ana']))).toEqual({ link: ['/marketplace', 'a1'] });
  });
});

describe('NotificacaoService', () => {
  const usuario = signal<UsuarioResumo | null>(null);
  const eventos = new Subject<{ tipo: string; dados: unknown }>();
  const sincronizar = new Subject<void>();
  let servico: NotificacaoService;
  let http: HttpTestingController;

  const aoVivoFalso = {
    sincronizar,
    eventos: <T>(tipo: string): Observable<T> =>
      eventos.pipe(
        filter((evento) => evento.tipo === tipo),
        map((evento) => evento.dados as T),
      ),
  };

  beforeEach(() => {
    usuario.set({
      id: 'ana',
      username: 'ana',
      nomeCompleto: 'Ana',
      nomeExibicao: null,
      email: 'ana@exemplo.com',
      fotoUrl: null,
      administrador: false,
    });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuario } },
        { provide: AoVivoService, useValue: aoVivoFalso },
      ],
    });
    servico = TestBed.inject(NotificacaoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('conta as não lidas e recarrega a cada evento e a cada nova conexão', () => {
    expect(servico.naoLidas()).toBe(0);

    servico.recarregarNaoLidas();
    http.expectOne('/api/notificacoes/nao-lidas').flush({ total: 2 });
    expect(servico.naoLidas()).toBe(2);

    eventos.next({ tipo: 'notificacoes', dados: {} });
    http.expectOne('/api/notificacoes/nao-lidas').flush({ total: 3 });
    eventos.next({ tipo: 'mensagem', dados: {} }); // de outro assunto: não pergunta
    http.expectNone('/api/notificacoes/nao-lidas');
    sincronizar.next();
    http.expectOne('/api/notificacoes/nao-lidas').flush({ total: 1 });

    expect(servico.naoLidas()).toBe(1);
  });

  it('não mostra a contagem de outra conta e não pede nada sem ninguém logado', () => {
    servico.recarregarNaoLidas();
    http.expectOne('/api/notificacoes/nao-lidas').flush({ total: 4 });

    usuario.set(null);
    expect(servico.naoLidas()).toBe(0);
    servico.recarregarNaoLidas();
    http.expectNone('/api/notificacoes/nao-lidas');
  });

  it('marcar como lidas deixa no contador só as que chegaram depois', () => {
    servico.recarregarNaoLidas();
    http.expectOne('/api/notificacoes/nao-lidas').flush({ total: 5 });

    let restantes: number | undefined;
    servico.marcarLidas('2026-11-19T12:00:00.123456Z').subscribe((total) => (restantes = total));
    const leitura = http.expectOne('/api/notificacoes/leitura');
    expect(leitura.request.method).toBe('POST');
    expect(leitura.request.body).toEqual({ ate: '2026-11-19T12:00:00.123456Z' });
    leitura.flush({ total: 1 });

    expect(restantes).toBe(1);
    expect(servico.naoLidas()).toBe(1);
  });

  it('pede as páginas pelo cursor', () => {
    servico.listar().subscribe();
    const primeira = http.expectOne((req) => req.url === '/api/notificacoes');
    expect(primeira.request.params.has('cursor')).toBe(false);
    primeira.flush({ itens: [], proximo: null, maisNovaEm: null });

    servico.listar('abc').subscribe();
    const segunda = http.expectOne((req) => req.url === '/api/notificacoes');
    expect(segunda.request.params.get('cursor')).toBe('abc');
    segunda.flush({ itens: [], proximo: null, maisNovaEm: null });
  });
});
