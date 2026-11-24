import { Injectable } from '@angular/core';
import { CodigoPendente } from '../codigo-por-email';

/** O e-mail que está esperando o código de verificação, do cadastro ou do login até a tela `/verificar-email`. */
@Injectable({ providedIn: 'root' })
export class VerificacaoPendente extends CodigoPendente {
  constructor() {
    super('campusconnect.verificacao');
  }
}
