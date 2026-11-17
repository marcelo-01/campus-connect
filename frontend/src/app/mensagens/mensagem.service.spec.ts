import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, filter, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioResumo } from '../auth/sessao';
import { AoVivoService } from '../shared/aovivo/aovivo.service';
import {
  Mensagem,
  MensagemService,
  ResumoConversa,
  compararInstantes,
  cortarPrevia,
  mesclar,
  previa,
  rotuloDoDia,
  separarPorDia,
  tamanhoDaMensagem,
} from './mensagem.service';

function mensagem(id: string, enviadoEm: string, conteudo: string | null = id): Mensagem {
  return { id, conversaId: 'c1', remetenteId: 'ana', conteudo, enviadoEm, apagada: conteudo === null };
}

function resumo(ultima: Partial<ResumoConversa['ultima']>): ResumoConversa {
  return {
    id: 'c1',
    pessoa: { id: 'bia', username: 'bia', nomeCompleto: 'Bia Souza', fotoUrl: null },
    ultima: { mensagemId: 'm1', texto: 'Oi', minha: false, enviadaEm: '2026-11-16T12:00:00Z', apagada: false, ...ultima },
    naoLida: false,
  };
}

describe('funções de mensagens', () => {
  it('compara instantes até o microssegundo, mesmo com frações de tamanhos diferentes', () => {
    expect(compararInstantes('2026-11-16T22:13:36.2Z', '2026-11-16T22:13:36.215Z')).toBeLessThan(0);
    expect(compararInstantes('2026-11-16T22:13:36.221Z', '2026-11-16T22:13:36.221657Z')).toBeLessThan(0);
    expect(compararInstantes('2026-11-16T22:13:37Z', '2026-11-16T22:13:36.999999Z')).toBeGreaterThan(0);
    expect(compararInstantes('2026-11-16T22:13:36.5Z', '2026-11-16T22:13:36.500Z')).toBe(0);
  });

  it('mescla sem repetir, com a versão nova valendo e na ordem do servidor', () => {
    const atuais = [mensagem('b', '2026-11-16T12:00:01Z'), mensagem('c', '2026-11-16T12:00:02Z')];
    const novas = [mensagem('a', '2026-11-16T12:00:00.5Z'), mensagem('c', '2026-11-16T12:00:02Z', null)];

    const resultado = mesclar(atuais, novas);

    expect(resultado.map((m) => m.id)).toEqual(['a', 'b', 'c']);
    expect(resultado[2].apagada).toBe(true);
    // A mesma mensagem que chegou pela conexão ao vivo e pela resposta do envio aparece uma vez só.
    expect(mesclar(resultado, [resultado[1]])).toHaveLength(3);
  });

  it('agrupa por dia com "Hoje", "Ontem" e a data', () => {
    const agora = new Date(2026, 10, 16, 15, 0);
    const iso = (dia: number, hora: number) => new Date(2026, 10, dia, hora).toISOString();

    const grupos = separarPorDia(
      [mensagem('a', iso(3, 10)), mensagem('b', iso(15, 9)), mensagem('c', iso(15, 23)), mensagem('d', iso(16, 8))],
      agora,
    );

    expect(grupos.map((grupo) => [grupo.dia, grupo.mensagens.map((m) => m.id)])).toEqual([
      ['3 de novembro', ['a']],
      ['Ontem', ['b', 'c']],
      ['Hoje', ['d']],
    ]);
    expect(rotuloDoDia(new Date(2025, 2, 12).toISOString(), agora)).toBe('12 de março de 2025');
  });

  it('monta a prévia da lista', () => {
    expect(previa(resumo({ texto: 'Oi, tudo bem?' }))).toBe('Oi, tudo bem?');
    expect(previa(resumo({ texto: 'Beleza', minha: true }))).toBe('Você: Beleza');
    expect(previa(resumo({ texto: null, apagada: true, minha: true }))).toBe('Mensagem apagada');
  });

  it('corta a prévia como o servidor e conta emoji como um caractere', () => {
    expect(cortarPrevia('Oi,\n\n  tudo   bem?')).toBe('Oi, tudo bem?');
    expect(cortarPrevia('a'.repeat(130))).toBe(`${'a'.repeat(120)}…`);
    expect(tamanhoDaMensagem('  😀😀 ')).toBe(2);
  });
});

describe('MensagemService', () => {
  const usuario = signal<UsuarioResumo | null>(null);
  const eventos = new Subject<{ tipo: string; dados: unknown }>();
  const sincronizar = new Subject<void>();
  let servico: MensagemService;
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
    servico = TestBed.inject(MensagemService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('conta as conversas não lidas e recarrega a cada evento e a cada nova conexão', () => {
    expect(servico.naoLidas()).toBe(0);

    servico.recarregarNaoLidas();
    http.expectOne('/api/conversas/nao-lidas').flush({ total: 2 });
    expect(servico.naoLidas()).toBe(2);

    eventos.next({ tipo: 'mensagem', dados: {} });
    http.expectOne('/api/conversas/nao-lidas').flush({ total: 3 });
    eventos.next({ tipo: 'leitura', dados: {} });
    http.expectOne('/api/conversas/nao-lidas').flush({ total: 1 });
    sincronizar.next();
    http.expectOne('/api/conversas/nao-lidas').flush({ total: 0 });

    expect(servico.naoLidas()).toBe(0);
  });

  it('não mostra a contagem de outra conta e não pede nada sem ninguém logado', () => {
    servico.recarregarNaoLidas();
    http.expectOne('/api/conversas/nao-lidas').flush({ total: 4 });

    usuario.set(null);
    expect(servico.naoLidas()).toBe(0);
    servico.recarregarNaoLidas();
    http.expectNone('/api/conversas/nao-lidas');
  });

  it('usa os endereços da API', () => {
    servico.listar({ busca: '  bia ', soNaoLidas: true, cursor: 'abc' }).subscribe();
    const lista = http.expectOne((req) => req.url === '/api/conversas');
    expect(lista.request.params.get('q')).toBe('bia');
    expect(lista.request.params.get('filtro')).toBe('nao-lidas');
    expect(lista.request.params.get('cursor')).toBe('abc');
    lista.flush({ itens: [], proximo: null });

    servico.abrirCom('bia').subscribe();
    expect(http.expectOne('/api/conversas/com/bia').request.method).toBe('POST');

    servico.enviar('c1', 'Oi').subscribe();
    const envio = http.expectOne('/api/conversas/c1/mensagens');
    expect(envio.request.body).toEqual({ conteudo: 'Oi' });

    servico.lerAte('c1', 'm9').subscribe();
    expect(http.expectOne('/api/conversas/c1/leitura').request.body).toEqual({ ate: 'm9' });

    servico.apagar('c1', 'm9').subscribe();
    expect(http.expectOne('/api/conversas/c1/mensagens/m9').request.method).toBe('DELETE');
  });
});
