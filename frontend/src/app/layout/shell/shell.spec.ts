import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { UsuarioResumo } from '../../auth/sessao';
import { BolhaService } from '../../bolhas/bolha.service';
import { ConexaoService } from '../../conexoes/conexao.service';
import { MensagemService } from '../../mensagens/mensagem.service';
import { AoVivoService } from '../../shared/aovivo/aovivo.service';
import { Shell } from './shell';

@Component({ template: '' })
class Vazia {}

/**
 * A gaveta do menu nas telas estreitas. O CSS global não entra no teste, então o que se confere aqui é o estado
 * (`.menu-aberto`, `aria-expanded`) e o foco, não se a gaveta aparece.
 */
describe('Shell', () => {
  let fixture: ComponentFixture<Shell>;
  let raiz: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Vazia }]),
        { provide: AuthService, useValue: { usuario: signal<UsuarioResumo | null>(null), sair: vi.fn() } },
        { provide: AoVivoService, useValue: { conectarComo: vi.fn() } },
        { provide: BolhaService, useValue: { minhas: signal(null), recarregarMinhas: vi.fn() } },
        { provide: ConexaoService, useValue: { totalRecebidos: signal(2), recarregarMinhas: vi.fn() } },
        { provide: MensagemService, useValue: { naoLidas: signal(0), recarregarNaoLidas: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(Shell);
    raiz = fixture.nativeElement as HTMLElement;
    document.body.appendChild(raiz);
    await fixture.whenStable();
  });

  afterEach(() => raiz.remove());

  const app = () => raiz.querySelector('.app')!;
  const botaoMenu = () => raiz.querySelector<HTMLButtonElement>('.menu-botao')!;
  const botaoFechar = () => raiz.querySelector<HTMLButtonElement>('.menu-fechar')!;

  async function abrir(): Promise<void> {
    botaoMenu().click();
    await fixture.whenStable();
  }

  it('abre a gaveta pelo botão de menu e põe o foco no "×" dela', async () => {
    expect(app().classList).not.toContain('menu-aberto');
    expect(botaoMenu().getAttribute('aria-expanded')).toBe('false');

    await abrir();

    expect(app().classList).toContain('menu-aberto');
    expect(botaoMenu().getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(botaoFechar());
  });

  it('fecha no Esc e devolve o foco ao botão de menu', async () => {
    await abrir();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();

    expect(app().classList).not.toContain('menu-aberto');
    expect(document.activeElement).toBe(botaoMenu());
  });

  it('fecha no "×" e no toque no fundo escurecido', async () => {
    await abrir();
    botaoFechar().click();
    await fixture.whenStable();
    expect(app().classList).not.toContain('menu-aberto');

    await abrir();
    raiz.querySelector<HTMLElement>('.menu-fundo')!.click();
    await fixture.whenStable();
    expect(app().classList).not.toContain('menu-aberto');
  });

  it('fecha ao tocar num link, mesmo o da página em que a pessoa já está', async () => {
    await abrir();

    // "Início" é a página atual: o link não gera navegação, e quem fecha a gaveta é o clique.
    raiz.querySelector<HTMLAnchorElement>('.sidebar a.nav-item[href="/"]')!.click();
    await fixture.whenStable();

    expect(app().classList).not.toContain('menu-aberto');
    expect(document.activeElement).toBe(botaoMenu());
  });

  it('fecha quando a navegação começa por outro caminho', async () => {
    await abrir();

    await TestBed.inject(Router).navigateByUrl('/salvos');
    await fixture.whenStable();

    expect(app().classList).not.toContain('menu-aberto');
  });

  it('mostra na barra de baixo o número de pedidos de conexão, como no menu lateral', () => {
    const conexoes = raiz.querySelector<HTMLAnchorElement>('.barra-inferior a[href="/conexoes"]')!;

    expect(conexoes.querySelector('.badge')?.textContent?.trim()).toBe('2');
    expect(conexoes.getAttribute('aria-label')).toBe('Conexões, 2 pedidos recebidos');
  });
});
