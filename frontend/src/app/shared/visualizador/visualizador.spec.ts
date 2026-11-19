import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Visualizador } from './visualizador';
import { Foto, VisualizadorService, descreverFoto, rotuloParaAmpliar } from './visualizador.service';

@Component({ template: '' })
class Vazia {}

const FOTOS: Foto[] = [
  { url: '/api/arquivos/a.jpg', descricao: 'Foto 1 de 3 da publicação de Ana' },
  { url: '/api/arquivos/b.jpg', descricao: 'Foto 2 de 3 da publicação de Ana' },
  { url: '/api/arquivos/c.jpg', descricao: 'Foto 3 de 3 da publicação de Ana' },
];

describe('VisualizadorService', () => {
  let servico: VisualizadorService;

  beforeEach(() => {
    servico = TestBed.inject(VisualizadorService);
  });

  it('abre na foto pedida, sem passar do começo nem do fim', () => {
    servico.abrir(FOTOS, 1);
    expect(servico.foto()?.url).toBe('/api/arquivos/b.jpg');

    servico.proxima();
    servico.proxima();
    expect(servico.exibicao()?.indice).toBe(2);
    servico.anterior();
    servico.anterior();
    servico.anterior();
    expect(servico.exibicao()?.indice).toBe(0);

    servico.abrir(FOTOS, 9);
    expect(servico.exibicao()?.indice).toBe(2);
  });

  it('não abre sem fotos e fecha', () => {
    servico.abrir([]);
    expect(servico.exibicao()).toBeNull();

    servico.abrir(FOTOS, 0, { redonda: true });
    expect(servico.exibicao()?.redonda).toBe(true);
    servico.fechar();
    expect(servico.foto()).toBeNull();
  });

  it('descreve a foto e o botão que a abre', () => {
    expect(descreverFoto(0, 1, 'da publicação de Ana')).toBe('Foto da publicação de Ana');
    expect(descreverFoto(1, 3, 'do anúncio Aulas')).toBe('Foto 2 de 3 do anúncio Aulas');
    expect(rotuloParaAmpliar(0, 1, 'da publicação de Ana')).toBe('Ampliar a foto da publicação de Ana');
    expect(rotuloParaAmpliar(2, 4, 'da publicação de Ana')).toBe('Ampliar a foto 3 de 4 da publicação de Ana');
  });
});

/** O jsdom não tem `showModal`: o componente cai no atributo `open`, que é o que se confere aqui. */
describe('Visualizador', () => {
  let fixture: ComponentFixture<Visualizador>;
  let servico: VisualizadorService;
  let raiz: HTMLElement;
  let empilhar: ReturnType<typeof vi.spyOn>;
  let voltar: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: Vazia }])] });
    empilhar = vi.spyOn(history, 'pushState').mockImplementation(() => undefined);
    voltar = vi.spyOn(history, 'back').mockImplementation(() => undefined);
    fixture = TestBed.createComponent(Visualizador);
    servico = TestBed.inject(VisualizadorService);
    raiz = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => vi.restoreAllMocks());

  const dialogo = () => raiz.querySelector('dialog')!;
  const foto = () => raiz.querySelector<HTMLImageElement>('.visualizador-foto');
  const botao = (rotulo: string) => raiz.querySelector<HTMLButtonElement>(`button[aria-label="${rotulo}"]`)!;

  async function abrir(indice = 0): Promise<void> {
    servico.abrir(FOTOS, indice);
    await fixture.whenStable();
  }

  async function disparar(evento: Event, alvo: Element = dialogo()): Promise<void> {
    alvo.dispatchEvent(evento);
    await fixture.whenStable();
  }

  function toque(tipo: string, x: number, y: number): Event {
    return Object.assign(new Event(tipo, { bubbles: true }), {
      isPrimary: true,
      pointerType: 'touch',
      clientX: x,
      clientY: y,
    });
  }

  it('mostra a foto, o contador e as setas, e passa de uma para outra', async () => {
    expect(dialogo().hasAttribute('open')).toBe(false);

    await abrir(0);

    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(foto()?.getAttribute('src')).toBe('/api/arquivos/a.jpg');
    expect(foto()?.alt).toBe('Foto 1 de 3 da publicação de Ana');
    expect(raiz.querySelector('.visualizador-contador')?.textContent?.trim()).toBe('1 de 3');
    expect(botao('Foto anterior').disabled).toBe(true);

    botao('Próxima foto').click();
    await fixture.whenStable();
    expect(foto()?.getAttribute('src')).toBe('/api/arquivos/b.jpg');

    await disparar(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(raiz.querySelector('.visualizador-contador')?.textContent?.trim()).toBe('3 de 3');
    expect(botao('Próxima foto').disabled).toBe(true);
    await disparar(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(servico.exibicao()?.indice).toBe(1);
  });

  it('com uma foto só, não mostra contador nem setas', async () => {
    servico.abrir([FOTOS[0]], 0, { redonda: true });
    await fixture.whenStable();

    expect(raiz.querySelector('.visualizador-contador')?.textContent?.trim()).toBe('');
    expect(raiz.querySelector('.visualizador-seta')).toBeNull();
    expect(foto()?.classList).toContain('redonda');
  });

  it('fecha no ×, no Esc e no clique fora da foto, e não na foto', async () => {
    await abrir();
    botao('Fechar').click();
    await fixture.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(false);

    await abrir();
    const esc = new Event('cancel', { cancelable: true });
    await disparar(esc);
    expect(esc.defaultPrevented).toBe(true);
    expect(servico.exibicao()).toBeNull();

    await abrir();
    await disparar(new MouseEvent('click', { bubbles: true }), foto()!);
    expect(servico.exibicao()).not.toBeNull();
    await disparar(new MouseEvent('click', { bubbles: true }));
    expect(servico.exibicao()).toBeNull();
  });

  it('desliza para o lado para trocar de foto e para baixo para fechar, sem o clique depois fechar', async () => {
    await abrir(0);

    await disparar(toque('pointerdown', 300, 200));
    await disparar(toque('pointerup', 180, 210));
    expect(servico.exibicao()?.indice).toBe(1);
    await disparar(new MouseEvent('click', { bubbles: true }));
    expect(servico.exibicao()).not.toBeNull();

    await disparar(toque('pointerdown', 200, 100));
    await disparar(toque('pointerup', 210, 260));
    expect(servico.exibicao()).toBeNull();
  });

  it('o voltar do navegador fecha, e fechar pelo × tira a entrada que ele pôs no histórico', async () => {
    await abrir();
    expect(empilhar).toHaveBeenCalledTimes(1);
    expect(empilhar.mock.calls[0][0]).toMatchObject({ visualizador: true });

    // O voltar já tirou a entrada: fechar não volta de novo.
    window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
    await fixture.whenStable();
    expect(servico.exibicao()).toBeNull();
    expect(voltar).not.toHaveBeenCalled();

    await abrir();
    botao('Fechar').click();
    await fixture.whenStable();
    expect(voltar).toHaveBeenCalledTimes(1);
  });

  it('fecha quando outra tela abre por baixo', async () => {
    await abrir();

    await TestBed.inject(Router).navigateByUrl('/entrar');
    await fixture.whenStable();

    expect(servico.exibicao()).toBeNull();
    expect(voltar).not.toHaveBeenCalled();
  });
});
