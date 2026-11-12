import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { UsuarioResumo } from '../auth/sessao';
import {
  ConexaoService,
  MinhasConexoes,
  PessoaNaConexao,
  SituacaoConexao,
  casaComBuscaPessoa,
  conexoesPorExtenso,
  primeiroNome,
  recadoDaAcao,
  situacoesPorPessoa,
} from './conexao.service';

function pessoa(id: string, nomeCompleto = id, username = id): PessoaNaConexao {
  return { id, username, nomeCompleto, fotoUrl: null, desde: '2026-11-10T12:00:00Z' };
}

function conta(id: string): UsuarioResumo {
  return {
    id,
    username: id,
    nomeCompleto: id,
    nomeExibicao: null,
    email: `${id}@exemplo.com`,
    fotoUrl: null,
    administrador: false,
  };
}

const LISTAS: MinhasConexoes = {
  conexoes: [pessoa('bia')],
  recebidos: [pessoa('caio'), pessoa('davi')],
  enviados: [pessoa('eva')],
};

describe('funções de conexão', () => {
  it('tira a situação de cada pessoa das listas', () => {
    const situacoes = situacoesPorPessoa(LISTAS);

    expect(situacoes.get('bia')).toBe('CONECTADOS');
    expect(situacoes.get('caio')).toBe('PEDIDO_RECEBIDO');
    expect(situacoes.get('eva')).toBe('PEDIDO_ENVIADO');
    expect(situacoes.get('zeca')).toBeUndefined();
  });

  it('busca no nome e no username, sem diferenciar acento nem maiúscula e sem o @', () => {
    const ana = pessoa('1', 'Ana Beatriz Casiano', 'ana.bc');

    expect(casaComBuscaPessoa(ana, 'beatriz')).toBe(true);
    expect(casaComBuscaPessoa(ana, ' @ANA.B ')).toBe(true);
    expect(casaComBuscaPessoa(pessoa('2', 'João Vitor', 'jv'), 'joao')).toBe(true);
    expect(casaComBuscaPessoa(ana, 'caio')).toBe(false);
    expect(casaComBuscaPessoa(ana, '  ')).toBe(true);
  });

  it('escreve o total e o primeiro nome', () => {
    expect(conexoesPorExtenso(1)).toBe('1 conexão');
    expect(conexoesPorExtenso(0)).toBe('0 conexões');
    expect(primeiroNome('  Ana Beatriz Casiano ')).toBe('Ana');
  });

  it('avisa como a relação ficou, mesmo quando a tela estava desatualizada', () => {
    expect(recadoDaAcao('conectar', 'PEDIDO_ENVIADO', 'Ana')).toBe('Pedido enviado para Ana');
    expect(recadoDaAcao('conectar', 'CONECTADOS', 'Ana')).toBe('Conexão feita com Ana');
    expect(recadoDaAcao('recusar', 'NENHUMA', 'Ana')).toBe('Pedido recusado');
    expect(recadoDaAcao('cancelarPedido', 'NENHUMA', 'Ana')).toBe('Pedido cancelado');
    expect(recadoDaAcao('desfazer', 'NENHUMA', 'Ana')).toBe('Conexão desfeita');
    // A Ana aceitou o pedido que ia ser cancelado.
    expect(recadoDaAcao('cancelarPedido', 'CONECTADOS', 'Ana')).toBe(
      'Ana já tinha aceitado: a conexão está feita',
    );
    expect(recadoDaAcao('desfazer', 'PEDIDO_RECEBIDO', 'Ana')).toBe('Ana enviou um pedido para você');
  });
});

describe('ConexaoService', () => {
  const usuario = signal<UsuarioResumo | null>(conta('ana'));
  let servico: ConexaoService;
  let http: HttpTestingController;

  beforeEach(() => {
    usuario.set(conta('ana'));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { usuario } }],
    });
    servico = TestBed.inject(ConexaoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carrega as listas de quem está logado e conta os pedidos recebidos', () => {
    expect(servico.minhas()).toBeNull();
    expect(servico.situacoes()).toBeNull();

    servico.recarregarMinhas();
    http.expectOne('/api/conexoes/minhas').flush(LISTAS);

    expect(servico.minhas()).toEqual(LISTAS);
    expect(servico.totalRecebidos()).toBe(2);
    expect(servico.situacoes()?.get('bia')).toBe('CONECTADOS');
  });

  it('fica com a recarga mais nova quando duas se cruzam', () => {
    servico.recarregarMinhas();
    servico.recarregarMinhas();

    const [antiga, nova] = http.match('/api/conexoes/minhas');
    expect(antiga.cancelled).toBe(true);
    nova.flush(LISTAS);

    expect(servico.minhas()).toEqual(LISTAS);
  });

  it('não mostra as listas de outra conta', () => {
    servico.recarregarMinhas();
    http.expectOne('/api/conexoes/minhas').flush(LISTAS);

    usuario.set(conta('bia'));

    expect(servico.minhas()).toBeNull();
    expect(servico.totalRecebidos()).toBe(0);
  });

  it('não pede nada sem ninguém logado', () => {
    usuario.set(null);

    servico.recarregarMinhas();

    http.expectNone('/api/conexoes/minhas');
  });

  it('cada ação usa o endereço certo, devolve a situação e recarrega as listas', () => {
    const acoes = [
      { chamar: () => servico.conectar('bia'), metodo: 'POST', url: '/api/conexoes/com/bia' },
      { chamar: () => servico.recusar('bia'), metodo: 'POST', url: '/api/conexoes/com/bia/recusa' },
      { chamar: () => servico.cancelarPedido('bia'), metodo: 'DELETE', url: '/api/conexoes/com/bia/pedido' },
      { chamar: () => servico.desfazer('bia'), metodo: 'DELETE', url: '/api/conexoes/com/bia' },
    ];

    for (const acao of acoes) {
      let situacao: SituacaoConexao | undefined;
      acao.chamar().subscribe((resposta) => (situacao = resposta));

      const requisicao = http.expectOne(acao.url);
      expect(requisicao.request.method).toBe(acao.metodo);
      requisicao.flush({ situacao: 'NENHUMA' });

      expect(situacao).toBe('NENHUMA');
      http.expectOne('/api/conexoes/minhas').flush(LISTAS);
    }
  });
});
