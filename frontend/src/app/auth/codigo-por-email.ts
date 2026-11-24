import { DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';

/**
 * O que as telas do código mandado para o e-mail têm em comum: a de confirmar o e-mail (`/verificar-email`) e a de
 * redefinir a senha (`/redefinir-senha`).
 */

/** Mesmo valor de `campusconnect.verificacao.intervalo-reenvio` no backend. */
export const INTERVALO_REENVIO_MS = 60_000;

/** Mesmas mensagens do `CodigoRequest` no backend. */
export const MENSAGENS_CODIGO: Record<string, string> = {
  required: 'Digite o código que mandamos para o seu e-mail.',
  pattern: 'O código tem 6 números.',
};

export interface Pendente {
  email: string;
  /** Quando dá para pedir outro código (epoch em ms). Antes disso, o servidor responderia 429. */
  liberaReenvioEm: number;
}

/**
 * O e-mail que está esperando um código, da tela que pediu o código até a que o confere.
 *
 * Fica também no `sessionStorage` da aba, para a tela sobreviver a um F5. Se o navegador bloquear o armazenamento,
 * a tela continua funcionando enquanto a aba não recarrega.
 */
export abstract class CodigoPendente {
  private readonly chave: string;
  private atual: Pendente | null;

  /** @param chave onde o pedido fica no `sessionStorage` */
  protected constructor(chave: string) {
    this.chave = chave;
    this.atual = ler(chave);
  }

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
      sessionStorage.removeItem(this.chave);
    } catch {
      // Sem armazenamento, não há o que apagar.
    }
  }

  private guardar(pendente: Pendente): void {
    this.atual = pendente;
    try {
      sessionStorage.setItem(this.chave, JSON.stringify(pendente));
    } catch {
      // Sem armazenamento, o e-mail fica só na memória.
    }
  }
}

function ler(chave: string): Pendente | null {
  try {
    const salvo = JSON.parse(sessionStorage.getItem(chave) ?? 'null') as Partial<Pendente> | null;
    return typeof salvo?.email === 'string' && typeof salvo.liberaReenvioEm === 'number'
      ? { email: salvo.email, liberaReenvioEm: salvo.liberaReenvioEm }
      : null;
  } catch {
    return null;
  }
}

/**
 * Deixa no campo do código só os números, até 6: o código colado do e-mail pode vir com espaço ou hífen. Quando
 * chegam os 6, chama `aoCompletar`. Chame no construtor do componente, que é quando a inscrição se prende a ele.
 */
export function prepararCampoCodigo(campo: FormControl<string>, aoCompletar: () => void): void {
  campo.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
    const digitos = valor.replace(/\D/g, '').slice(0, 6);
    if (digitos !== valor) {
      campo.setValue(digitos);
    } else if (digitos.length === 6) {
      aoCompletar();
    }
  });
}

/**
 * A contagem até poder pedir outro código, que anda de segundo em segundo. Crie no construtor do componente: o
 * relógio para quando o componente sai da tela.
 */
export class ContagemReenvio {
  private readonly agora = signal(Date.now());
  private readonly liberaEm: WritableSignal<number>;

  /** Segundos até poder pedir outro código. */
  readonly segundos = computed(() =>
    Math.max(0, Math.ceil((this.liberaEm() - this.agora()) / 1000)),
  );

  /** A espera para mostrar: em segundos até 1 minuto, depois em minutos. */
  readonly texto = computed(() => {
    const segundos = this.segundos();
    return segundos <= 60 ? `${segundos} s` : `${Math.ceil(segundos / 60)} min`;
  });

  /** @param liberaEm quando dá para pedir outro código (epoch em ms) */
  constructor(liberaEm: number) {
    this.liberaEm = signal(liberaEm);
    const relogio = setInterval(() => this.agora.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(relogio));
  }

  /** O próximo pedido só vale daqui a `ms` milissegundos. */
  aguardar(ms: number): void {
    this.agora.set(Date.now());
    this.liberaEm.set(Date.now() + ms);
  }
}
