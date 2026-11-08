import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Moldura das telas de login e cadastro, copiada do `auth.jsx` do MVP: painel da marca à esquerda e o
 * cartão do formulário à direita. Cada tela coloca o próprio formulário no `<ng-content>`.
 */
@Component({
  selector: 'app-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  templateUrl: './auth-layout.html',
})
export class AuthLayout {}
