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

@Component({
  selector: 'app-icone-google',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    <svg viewBox="0 0 24 24" width="16" height="16"><path fill="#EA4335" d="M12 10.2v3.9h5.5a4.7 4.7 0 0 1-2 3.1l3.2 2.5c1.9-1.7 3-4.3 3-7.4 0-.7-.1-1.4-.2-2.1H12z"/><path fill="#34A853" d="M6.5 14.3l-.7.5-2.5 2A9.9 9.9 0 0 0 12 22c2.7 0 4.9-.9 6.6-2.4l-3.2-2.5c-.9.6-2 .9-3.4.9-2.6 0-4.8-1.7-5.5-4.1z"/><path fill="#FBBC05" d="M3.3 7.2A9.9 9.9 0 0 0 2 12c0 1.6.4 3.1 1.1 4.4l3.4-2.6a5.9 5.9 0 0 1 0-3.7L3.3 7.2z"/><path fill="#4285F4" d="M12 6c1.5 0 2.8.5 3.8 1.5l2.8-2.8C16.9 3.1 14.7 2 12 2 8.1 2 4.8 4.2 3.3 7.4l3.4 2.6C7.2 7.7 9.4 6 12 6z"/></svg>
  `,
})
export class IconeGoogle {}
