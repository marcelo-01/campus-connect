import { Injectable, computed, signal } from '@angular/core';

/** Uma foto para ver em tela cheia. */
export interface Foto {
  url: string;
  /** O texto alternativo: "Foto 2 de 3 da publicação de Ana". */
  descricao: string;
}

export interface Exibicao {
  fotos: readonly Foto[];
  indice: number;
  /** A foto de perfil aparece redonda, como no resto do app. */
  redonda: boolean;
}

/**
 * As fotos abertas no visualizador de tela cheia, que fica uma vez só na raiz do app. Quem tem fotos chama
 * {@link abrir}; o {@link Visualizador} mostra e cuida do teclado, dos gestos e do botão voltar.
 */
@Injectable({ providedIn: 'root' })
export class VisualizadorService {
  private readonly estado = signal<Exibicao | null>(null);

  /** O que está aberto; `null` com o visualizador fechado. */
  readonly exibicao = this.estado.asReadonly();
  readonly foto = computed(() => {
    const exibicao = this.estado();
    return exibicao ? exibicao.fotos[exibicao.indice] : null;
  });

  /** Abre na foto `indice`. Sem fotos, não abre. */
  abrir(fotos: readonly Foto[], indice = 0, opcoes: { redonda?: boolean } = {}): void {
    if (!fotos.length) {
      return;
    }
    this.estado.set({
      fotos: [...fotos],
      indice: Math.min(Math.max(0, indice), fotos.length - 1),
      redonda: opcoes.redonda ?? false,
    });
  }

  /** Não dá a volta: na primeira e na última, as setas param, como no X. */
  anterior(): void {
    this.irPara(-1);
  }

  proxima(): void {
    this.irPara(1);
  }

  fechar(): void {
    this.estado.set(null);
  }

  private irPara(passo: number): void {
    this.estado.update((atual) => {
      if (!atual) {
        return atual;
      }
      const indice = atual.indice + passo;
      return indice < 0 || indice >= atual.fotos.length ? atual : { ...atual, indice };
    });
  }
}

/** "Foto da publicação de Ana" ou "Foto 2 de 3 da publicação de Ana". */
export function descreverFoto(indice: number, total: number, deQue: string): string {
  return total > 1 ? `Foto ${indice + 1} de ${total} ${deQue}` : `Foto ${deQue}`;
}

/** O nome do botão que abre a foto: "Ampliar a foto 2 de 3 da publicação de Ana". */
export function rotuloParaAmpliar(indice: number, total: number, deQue: string): string {
  return total > 1 ? `Ampliar a foto ${indice + 1} de ${total} ${deQue}` : `Ampliar a foto ${deQue}`;
}
