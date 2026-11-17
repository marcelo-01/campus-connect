import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ARMAZENAMENTO, AparenciaService, CHAVE_APARENCIA, TEMA_ESCURO_DO_SISTEMA } from './aparencia.service';

/** Um `localStorage` em memória. */
class ArmazenamentoFalso {
  readonly itens = new Map<string, string>();
  getItem(chave: string): string | null {
    return this.itens.get(chave) ?? null;
  }
  setItem(chave: string, valor: string): void {
    this.itens.set(chave, valor);
  }
}

/** O `matchMedia('(prefers-color-scheme: dark)')`, com o aparelho trocando de tema quando o teste quiser. */
class SistemaFalso {
  matches = false;
  private readonly ouvintes = new Set<(evento: MediaQueryListEvent) => void>();
  addEventListener(_tipo: 'change', ouvinte: (evento: MediaQueryListEvent) => void): void {
    this.ouvintes.add(ouvinte);
  }
  removeEventListener(_tipo: 'change', ouvinte: (evento: MediaQueryListEvent) => void): void {
    this.ouvintes.delete(ouvinte);
  }
  trocar(escuro: boolean): void {
    this.matches = escuro;
    this.ouvintes.forEach((ouvinte) => ouvinte({ matches: escuro } as MediaQueryListEvent));
  }
}

describe('AparenciaService', () => {
  let armazenamento: ArmazenamentoFalso;
  let sistema: SistemaFalso;
  let raiz: HTMLElement;

  beforeEach(() => {
    armazenamento = new ArmazenamentoFalso();
    sistema = new SistemaFalso();
    TestBed.configureTestingModule({
      providers: [
        { provide: ARMAZENAMENTO, useValue: armazenamento },
        { provide: TEMA_ESCURO_DO_SISTEMA, useValue: sistema },
      ],
    });
    raiz = TestBed.inject(DOCUMENT).documentElement;
    delete raiz.dataset['theme'];
    delete raiz.dataset['accent'];
  });

  function iniciar(): AparenciaService {
    const servico = TestBed.inject(AparenciaService);
    TestBed.tick();
    return servico;
  }

  it('sem escolha salva, segue o aparelho com a Lavanda', () => {
    sistema.matches = true;
    const aparencia = iniciar();

    expect(aparencia.tema()).toBe('automatico');
    expect(aparencia.cor()).toBe('lavanda');
    expect(raiz.dataset['theme']).toBe('dark');
    expect(raiz.dataset['accent']).toBe('lavanda');
  });

  it('no automático, troca junto quando o aparelho troca de tema', () => {
    iniciar();
    expect(raiz.dataset['theme']).toBe('light');

    sistema.trocar(true);
    TestBed.tick();

    expect(raiz.dataset['theme']).toBe('dark');
  });

  it('com o tema escolhido à mão, ignora o aparelho', () => {
    const aparencia = iniciar();
    aparencia.escolherTema('claro');
    sistema.trocar(true);
    TestBed.tick();

    expect(raiz.dataset['theme']).toBe('light');
  });

  it('aplica e salva a escolha', () => {
    const aparencia = iniciar();

    aparencia.escolherTema('escuro');
    aparencia.escolherCor('verde');
    TestBed.tick();

    expect(raiz.dataset['theme']).toBe('dark');
    expect(raiz.dataset['accent']).toBe('verde');
    expect(JSON.parse(armazenamento.getItem(CHAVE_APARENCIA)!)).toEqual({ tema: 'escuro', cor: 'verde' });
  });

  it('lê a escolha salva', () => {
    armazenamento.setItem(CHAVE_APARENCIA, JSON.stringify({ tema: 'escuro', cor: 'rosa' }));
    const aparencia = iniciar();

    expect(aparencia.tema()).toBe('escuro');
    expect(raiz.dataset['accent']).toBe('rosa');
  });

  it('volta ao padrão o que não reconhece, sem perder o resto', () => {
    armazenamento.setItem(CHAVE_APARENCIA, JSON.stringify({ tema: 'escuro', cor: 'amarelo' }));
    const aparencia = iniciar();

    expect(aparencia.tema()).toBe('escuro');
    expect(aparencia.cor()).toBe('lavanda');
  });

  it('com o valor salvo quebrado, fica no padrão', () => {
    armazenamento.setItem(CHAVE_APARENCIA, '{quebrado');
    const aparencia = iniciar();

    expect(aparencia.tema()).toBe('automatico');
    expect(aparencia.cor()).toBe('lavanda');
  });

  it('continua funcionando quando o navegador não deixa salvar', () => {
    armazenamento.setItem = () => {
      throw new DOMException('Cheio', 'QuotaExceededError');
    };
    const aparencia = iniciar();

    aparencia.escolherCor('azul');
    TestBed.tick();

    expect(raiz.dataset['accent']).toBe('azul');
  });

  it('recebe a escolha feita em outra aba', () => {
    const aparencia = iniciar();
    armazenamento.setItem(CHAVE_APARENCIA, JSON.stringify({ tema: 'escuro', cor: 'turquesa' }));

    TestBed.inject(DOCUMENT).defaultView!.dispatchEvent(new StorageEvent('storage', { key: CHAVE_APARENCIA }));
    TestBed.tick();

    expect(aparencia.cor()).toBe('turquesa');
    expect(raiz.dataset['theme']).toBe('dark');
  });
});
