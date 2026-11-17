import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { Mensagem } from './mensagem.service';

/**
 * O que a conversa aberta conta para a lista ao lado, na página de Mensagens. Os eventos ao vivo já levam isso à
 * lista; daqui ela fica sabendo mesmo quando a conexão ao vivo está fora.
 */
@Injectable()
export class PainelMensagens {
  /** A conversa aberta, para a lista destacá-la. */
  readonly ativa = signal<string | null>(null);

  /** Uma mensagem que a própria pessoa acabou de enviar. */
  readonly enviada = new Subject<Mensagem>();

  /** A conversa que acabou de ser marcada como lida. */
  readonly lida = new Subject<string>();
}
