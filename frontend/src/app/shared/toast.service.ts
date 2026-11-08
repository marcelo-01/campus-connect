import { Injectable, signal } from '@angular/core';

const DURACAO_MS = 1800;

/** Aviso rápido no rodapé da tela, igual ao `useToast` do MVP. O `App` desenha a mensagem com a classe `.toast`. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly atual = signal<string | null>(null);
  private temporizador?: ReturnType<typeof setTimeout>;

  readonly mensagem = this.atual.asReadonly();

  mostrar(mensagem: string): void {
    this.atual.set(mensagem);
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.atual.set(null), DURACAO_MS);
  }
}
