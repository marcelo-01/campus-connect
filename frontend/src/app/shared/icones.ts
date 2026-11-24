import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/*
 * SVGs copiados do auth.jsx do MVP. O elemento do componente usa `display: contents` para não criar
 * caixa própria: o <svg> continua sendo filho direto do botão, como no MVP, e o CSS dele vale igual.
 */

/** Olho do campo de senha: aberto para "mostrar", riscado para "ocultar". */
@Component({
  selector: 'app-icone-olho',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    @if (riscado()) {
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9.9 4.24A9.1 9.1 0 0 1 12 4c7 0 10 8 10 8a18 18 0 0 1-2.16 3.19M6.7 6.7A18 18 0 0 0 2 12s3 8 10 8a9 9 0 0 0 5.3-1.7"/><path d="M1 1l22 22"/><path d="M9.5 9.5a3 3 0 0 0 4.2 4.2"/></svg>
    } @else {
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3-8 10-8 10 8 10 8-3 8-10 8-10-8-10-8z"/><circle cx="12" cy="12" r="3"/></svg>
    }
  `,
})
export class IconeOlho {
  readonly riscado = input(false);
}
