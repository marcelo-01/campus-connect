import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { BuscaService, PaginaPessoas } from './busca.service';
import { BuscaTopo, ESPERA_DAS_SUGESTOES } from './busca-topo';

@Component({ template: '' })
class Vazia {}

const ANA: PessoaPublica = { id: 'ana', username: 'ana.bc', nomeCompleto: 'Ana Beatriz', fotoUrl: null };
const MARIANA: PessoaPublica = { id: 'mari', username: 'mari', nomeCompleto: 'Mariana Lima', fotoUrl: null };

describe('BuscaTopo', () => {
  let fixture: ComponentFixture<BuscaTopo>;
  let raiz: HTMLElement;
  let router: Router;
  let pessoas: ReturnType<typeof vi.fn<(termo: string, cursor: string | null, limite: number | null) => unknown>>;

  beforeEach(async () => {
    pessoas = vi.fn(() => of<PaginaPessoas>({ itens: [ANA, MARIANA], proximo: null }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Vazia }]),
        { provide: BuscaService, useValue: { pessoas } },
        { provide: ESPERA_DAS_SUGESTOES, useValue: 0 },
      ],
    });
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(BuscaTopo);
    raiz = fixture.nativeElement as HTMLElement;
    document.body.appendChild(raiz);
    await fixture.whenStable();
  });

  afterEach(() => raiz.remove());

  const campo = () => raiz.querySelector<HTMLInputElement>('input')!;
  const lista = () => raiz.querySelector<HTMLElement>('[role="listbox"]');
  const opcoes = () =>
    [...raiz.querySelectorAll<HTMLElement>('[role="option"]')].map((opcao) =>
      [...opcao.querySelectorAll('.busca-opcao-nome, .busca-opcao-detalhe')].map((t) => t.textContent?.trim()).join(' '),
    );

  /** A espera das sugestões é zero, mas ainda passa por um timer. */
  async function esperar(): Promise<void> {
    await fixture.whenStable();
    await new Promise((pronto) => setTimeout(pronto, 5));
    await fixture.whenStable();
  }

  async function digitar(texto: string): Promise<void> {
    campo().focus();
    campo().value = texto;
    campo().dispatchEvent(new Event('input'));
    await esperar();
  }

  async function tecla(key: string): Promise<void> {
    campo().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    await esperar();
  }

  it('sugere pessoas enquanto a pessoa digita, com a busca na primeira linha', async () => {
    expect(lista()).toBeNull();

    await digitar('ana');

    expect(pessoas).toHaveBeenCalledWith('ana', null, 5);
    expect(campo().getAttribute('aria-expanded')).toBe('true');
    expect(opcoes()).toEqual(['Buscar “ana”', 'Ana Beatriz @ana.bc', 'Mariana Lima @mari']);
  });

  it('as setas escolhem a linha, e o Enter abre o perfil da pessoa e limpa o campo', async () => {
    await digitar('ana');

    await tecla('ArrowDown');
    expect(campo().getAttribute('aria-activedescendant')).toBe('busca-opcao-0');
    await tecla('ArrowDown');
    expect(campo().getAttribute('aria-activedescendant')).toBe('busca-opcao-1');
    await tecla('ArrowUp');
    await tecla('ArrowUp');
    // Acima da primeira linha, volta para a última.
    expect(campo().getAttribute('aria-activedescendant')).toBe('busca-opcao-2');
    await tecla('ArrowUp');
    await tecla('Enter');

    expect(router.url).toBe('/perfil/ana.bc');
    expect(campo().value).toBe('');
    expect(lista()).toBeNull();
  });

  it('o Enter sem linha escolhida abre a página de busca, e o campo mostra o termo dela', async () => {
    await digitar('  bolsa de estágio ');
    await tecla('Enter');

    expect(router.url).toBe('/busca?q=bolsa%20de%20est%C3%A1gio');
    expect(campo().value).toBe('bolsa de estágio');
    expect(lista()).toBeNull();
  });

  it('na página de busca, uma busca nova continua na mesma aba', async () => {
    await router.navigateByUrl('/busca?q=ana&aba=pessoas');
    await fixture.whenStable();
    expect(campo().value).toBe('ana');

    await digitar('bia');
    await tecla('Enter');

    expect(router.url).toBe('/busca?q=bia&aba=pessoas');
  });

  it('o Esc fecha a lista, e a seta para baixo a abre de novo', async () => {
    await digitar('ana');

    await tecla('Escape');
    expect(lista()).toBeNull();
    expect(campo().getAttribute('aria-expanded')).toBe('false');

    await tecla('ArrowDown');
    expect(lista()).not.toBeNull();
  });

  it('sem nada digitado, não pede sugestões nem abre a lista', async () => {
    await digitar('   ');
    await tecla('Enter');

    expect(pessoas).not.toHaveBeenCalled();
    expect(lista()).toBeNull();
    expect(router.url).toBe('/');
  });

  it('Ctrl+K leva o foco ao campo de qualquer lugar da tela', async () => {
    const evento = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true });
    document.body.dispatchEvent(evento);
    await fixture.whenStable();

    expect(document.activeElement).toBe(campo());
    expect(evento.defaultPrevented).toBe(true);
  });
});
