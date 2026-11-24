import { Injectable } from '@angular/core';

const CHAVE = 'campusconnect.verificacao';

/** Mesmo valor de `campusconnect.verificacao.intervalo-reenvio` no backend. */
export const INTERVALO_REENVIO_MS = 60_000;

export interface Pendente {
  email: string;
  /** Quando dá para pedir outro código (epoch em ms). Antes disso, o servidor responderia 429. */
  liberaReenvioEm: number;
}

/**
 * O e-mail que está esperando o código, do cadastro ou do login até a tela `/verificar-email`.
 *
 * Fica também no `sessionStorage` da aba, para a tela sobreviver a um F5. Se o navegador bloquear o armazenamento,
 * a tela continua funcionando enquanto a aba não recarrega.
 */
@Injectable({ providedIn: 'root' })
export class VerificacaoPendente {
  private atual: Pendente | null = ler();

  get pendente(): Pendente | null {
    return this.atual;
  }

  /** Um código acabou de ser mandado para o e-mail (ou o servidor manda logo depois, no cadastro). */
  comecar(email: string): void {
    this.guardar({ email, liberaReenvioEm: Date.now() + INTERVALO_REENVIO_MS });
  }

  /** O próximo reenvio só vale depois de `ms` milissegundos. */
  esperar(ms: number): void {
    if (this.atual) {
      this.guardar({ ...this.atual, liberaReenvioEm: Date.now() + ms });
    }
  }

  terminar(): void {
    this.atual = null;
    try {
      sessionStorage.removeItem(CHAVE);
    } catch {
      // Sem armazenamento, não há o que apagar.
    }
  }

  private guardar(pendente: Pendente): void {
    this.atual = pendente;
    try {
      sessionStorage.setItem(CHAVE, JSON.stringify(pendente));
    } catch {
      // Sem armazenamento, o e-mail fica só na memória.
    }
  }
}

function ler(): Pendente | null {
  try {
    const salvo = JSON.parse(sessionStorage.getItem(CHAVE) ?? 'null') as Partial<Pendente> | null;
    return typeof salvo?.email === 'string' && typeof salvo.liberaReenvioEm === 'number'
      ? { email: salvo.email, liberaReenvioEm: salvo.liberaReenvioEm }
      : null;
  } catch {
    return null;
  }
}
