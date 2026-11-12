import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioResumo } from '../auth/sessao';
import { ToastService } from '../shared/toast.service';
import { BotaoConexao, PessoaDoBotao } from './botao-conexao';
import { ConexaoService, SituacaoConexao } from './conexao.service';

const ANA: UsuarioResumo = {
  id: 'ana',
  username: 'ana',
  nomeCompleto: 'Ana Beatriz',
  nomeExibicao: null,
  email: 'ana@exemplo.com',
  fotoUrl: null,
  administrador: false,
};

const BIA: PessoaDoBotao = { id: 'bia', nomeCompleto: 'Bia Souza' };

type Acao = (usuarioId: string) => Observable<SituacaoConexao>;

describe('BotaoConexao', () => {
  let situacoes: ReturnType<typeof signal<ReadonlyMap<string, SituacaoConexao> | null>>;
  let servico: {
    situacoes: typeof situacoes;
    conectar: ReturnType<typeof vi.fn<Acao>>;
    recusar: ReturnType<typeof vi.fn<Acao>>;
    cancelarPedido: ReturnType<typeof vi.fn<Acao>>;
    desfazer: ReturnType<typeof vi.fn<Acao>>;
  };
  let toast: { mostrar: ReturnType<typeof vi.fn<(mensagem: string) => void>> };

  beforeEach(() => {
    situacoes = signal<ReadonlyMap<string, SituacaoConexao> | null>(new Map());
    servico = {
      situacoes,
      conectar: vi.fn<Acao>(),
      recusar: vi.fn<Acao>(),
      cancelarPedido: vi.fn<Acao>(),
      desfazer: vi.fn<Acao>(),
    };
    toast = { mostrar: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { usuario: signal(ANA) } },
        { provide: ConexaoService, useValue: servico },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  async function montar(pessoa: PessoaDoBotao = BIA): Promise<ComponentFixture<BotaoConexao>> {
    const fixture = TestBed.createComponent(BotaoConexao);
    fixture.componentRef.setInput('pessoa', pessoa);
    await fixture.whenStable();
    return fixture;
  }

  function botoes(fixture: ComponentFixture<BotaoConexao>): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
  }

  function textos(fixture: ComponentFixture<BotaoConexao>): string[] {
    return botoes(fixture).map((botao) => botao.textContent?.trim() ?? '');
  }

  async function clicar(fixture: ComponentFixture<BotaoConexao>, texto: string): Promise<void> {
    const botao = botoes(fixture).find((b) => b.textContent?.trim() === texto);
    expect(botao, `botão "${texto}"`).toBeDefined();
    botao!.click();
    await fixture.whenStable();
  }

  it('não aparece antes de as listas carregarem, para não oferecer "Conectar" a quem já é conexão', async () => {
    situacoes.set(null);

    expect(botoes(await montar())).toEqual([]);
  });

  it('não aparece no perfil da própria pessoa', async () => {
    expect(botoes(await montar({ id: 'ana', nomeCompleto: 'Ana Beatriz' }))).toEqual([]);
  });

  it('mostra o botão de cada situação, com o nome da pessoa para leitores de tela', async () => {
    const fixture = await montar();
    expect(textos(fixture)).toEqual(['Conectar']);
    expect(botoes(fixture)[0].getAttribute('aria-label')).toBe('Conectar com Bia Souza');

    situacoes.set(new Map([['bia', 'PEDIDO_RECEBIDO']]));
    await fixture.whenStable();
    expect(textos(fixture)).toEqual(['Aceitar', 'Recusar']);
    expect(botoes(fixture)[1].getAttribute('aria-label')).toBe('Recusar o pedido de Bia Souza');

    situacoes.set(new Map([['bia', 'PEDIDO_ENVIADO']]));
    await fixture.whenStable();
    expect(textos(fixture)).toEqual(['Pedido enviado']);

    situacoes.set(new Map([['bia', 'CONECTADOS']]));
    await fixture.whenStable();
    expect(textos(fixture)).toEqual(['Conectado']);
  });

  it('fica desabilitado enquanto envia e mostra a situação que o servidor devolveu', async () => {
    const resposta = new Subject<SituacaoConexao>();
    servico.conectar.mockReturnValue(resposta);
    const fixture = await montar();
    const avisos: SituacaoConexao[] = [];
    fixture.componentInstance.mudou.subscribe((situacao) => avisos.push(situacao));

    await clicar(fixture, 'Conectar');

    expect(servico.conectar).toHaveBeenCalledWith('bia');
    expect(textos(fixture)).toEqual(['Enviando…']);
    expect(botoes(fixture)[0].disabled).toBe(true);

    resposta.next('PEDIDO_ENVIADO');
    resposta.complete();
    await fixture.whenStable();

    expect(textos(fixture)).toEqual(['Pedido enviado']);
    expect(toast.mostrar).toHaveBeenCalledWith('Pedido enviado para Bia');
    expect(avisos).toEqual(['PEDIDO_ENVIADO']);
  });

  it('pede confirmação antes de desfazer a conexão', async () => {
    situacoes.set(new Map([['bia', 'CONECTADOS']]));
    servico.desfazer.mockReturnValue(of('NENHUMA'));
    const fixture = await montar();

    await clicar(fixture, 'Conectado');
    expect(textos(fixture)).toEqual(['Desfazer conexão', 'Manter']);
    expect(servico.desfazer).not.toHaveBeenCalled();

    await clicar(fixture, 'Manter');
    expect(textos(fixture)).toEqual(['Conectado']);

    await clicar(fixture, 'Conectado');
    await clicar(fixture, 'Desfazer conexão');

    expect(servico.desfazer).toHaveBeenCalledWith('bia');
    expect(textos(fixture)).toEqual(['Conectar']);
    expect(toast.mostrar).toHaveBeenCalledWith('Conexão desfeita');
  });

  it('pede confirmação antes de cancelar o pedido', async () => {
    situacoes.set(new Map([['bia', 'PEDIDO_ENVIADO']]));
    servico.cancelarPedido.mockReturnValue(of('NENHUMA'));
    const fixture = await montar();

    await clicar(fixture, 'Pedido enviado');
    expect(servico.cancelarPedido).not.toHaveBeenCalled();
    await clicar(fixture, 'Cancelar pedido');

    expect(servico.cancelarPedido).toHaveBeenCalledWith('bia');
    expect(textos(fixture)).toEqual(['Conectar']);
  });

  it('recusa sem pedir confirmação', async () => {
    situacoes.set(new Map([['bia', 'PEDIDO_RECEBIDO']]));
    servico.recusar.mockReturnValue(of('NENHUMA'));
    const fixture = await montar();

    await clicar(fixture, 'Recusar');

    expect(servico.recusar).toHaveBeenCalledWith('bia');
    expect(toast.mostrar).toHaveBeenCalledWith('Pedido recusado');
  });

  it('mantém o botão como estava e avisa quando dá erro', async () => {
    servico.conectar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = await montar();

    await clicar(fixture, 'Conectar');

    expect(textos(fixture)).toEqual(['Conectar']);
    expect(botoes(fixture)[0].disabled).toBe(false);
    expect(toast.mostrar).toHaveBeenCalledWith('Não foi possível concluir. Tente de novo.');
  });
});
