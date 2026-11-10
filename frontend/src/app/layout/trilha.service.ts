import { Injectable, signal } from '@angular/core';

/**
 * Deixa uma tela trocar a trilha da barra do topo depois de carregar os dados, como a página da bolha,
 * que mostra "IFBA › Campus Eunápolis › ADS" igual ao `Topbar` do MVP. A troca vale até a próxima
 * navegação; sem ela, vale a `data.trilha` da rota.
 */
@Injectable({ providedIn: 'root' })
export class TrilhaService {
  private readonly sobrescrita = signal<string[] | null>(null);

  readonly atual = this.sobrescrita.asReadonly();

  definir(trilha: string[]): void {
    this.sobrescrita.set(trilha);
  }

  limpar(): void {
    this.sobrescrita.set(null);
  }
}
