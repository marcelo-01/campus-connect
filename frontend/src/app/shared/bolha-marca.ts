import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MarcaDaBolha, corDaBolha, siglaExibida } from '../bolhas/bolha.service';

const CLASSES = {
  /** O `BubbleDot` do MVP (22 px): menu, chips, cards. */
  pequena: 'bubble-dot',
  /** O `big-dot` (64 px) do `.bubble-hero`. */
  grande: 'big-dot',
  /** O círculo translúcido do destaque do Explorar (72 px). */
  destaque: 'explore-hero-marca',
} as const;

/**
 * O círculo de uma bolha: a foto, ou a sigla sobre a cor do tipo. Como o `Avatar`, o elemento do
 * componente não cria caixa, então o CSS do MVP vale direto no círculo.
 */
@Component({
  selector: 'app-bolha-marca',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    <div [class]="classe()" [style.background]="fundo()">
      @if (bolha().fotoUrl; as url) {
        <img [src]="url" alt="" />
      } @else {
        {{ texto() }}
      }
    </div>
  `,
})
export class BolhaMarca {
  readonly bolha = input.required<MarcaDaBolha>();
  readonly tamanho = input<keyof typeof CLASSES>('pequena');

  protected readonly classe = computed(() => CLASSES[this.tamanho()]);
  protected readonly texto = computed(() =>
    siglaExibida(this.bolha(), this.tamanho() === 'pequena' ? 2 : 4),
  );
  /** O destaque fica sobre o gradiente e mantém o próprio fundo translúcido. */
  protected readonly fundo = computed(() =>
    this.tamanho() === 'destaque' || this.bolha().fotoUrl ? null : corDaBolha(this.bolha().tipo),
  );
}
