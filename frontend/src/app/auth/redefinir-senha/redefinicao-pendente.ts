import { Injectable } from '@angular/core';
import { CodigoPendente } from '../codigo-por-email';

/** O e-mail que pediu o código para redefinir a senha, enquanto a tela `/redefinir-senha` espera por ele. */
@Injectable({ providedIn: 'root' })
export class RedefinicaoPendente extends CodigoPendente {
  constructor() {
    super('campusconnect.redefinicao');
  }
}
