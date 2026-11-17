/** Um evento SSE completo: o nome (`event:`) e o texto (`data:`). */
export interface EventoSse {
  tipo: string;
  dados: string;
}

/**
 * Lê um fluxo SSE que chega em pedaços. Um pedaço pode cortar uma linha, um evento ou até um caractere acentuado no
 * meio: o leitor guarda o que sobrou e completa com o pedaço seguinte.
 *
 * Segue o formato do `EventSource`: linhas `campo:valor`, eventos separados por uma linha em branco, várias linhas
 * `data:` juntadas com `\n`, comentários começando por `:` e quebras de linha `\n`, `\r\n` ou `\r`.
 */
export class LeitorSse {
  private readonly decodificador = new TextDecoder('utf-8');
  private resto = '';
  private tipo = '';
  private dados: string[] = [];

  /** Recebe um pedaço em bytes e devolve os eventos que ficaram completos. */
  ler(pedaco: Uint8Array): EventoSse[] {
    return this.lerTexto(this.decodificador.decode(pedaco, { stream: true }));
  }

  /** O mesmo que `ler`, para texto já decodificado. */
  lerTexto(texto: string): EventoSse[] {
    this.resto += texto;
    const eventos: EventoSse[] = [];
    let inicio = 0;
    for (let i = 0; i < this.resto.length; i++) {
      const caractere = this.resto[i];
      if (caractere !== '\n' && caractere !== '\r') {
        continue;
      }
      // Um `\r` no fim pode ser a primeira metade de um `\r\n`: espera o próximo pedaço.
      if (caractere === '\r' && i === this.resto.length - 1) {
        break;
      }
      const linha = this.resto.slice(inicio, i);
      if (caractere === '\r' && this.resto[i + 1] === '\n') {
        i++;
      }
      inicio = i + 1;
      const evento = this.processar(linha);
      if (evento) {
        eventos.push(evento);
      }
    }
    this.resto = this.resto.slice(inicio);
    return eventos;
  }

  private processar(linha: string): EventoSse | null {
    if (linha === '') {
      return this.despachar();
    }
    if (linha.startsWith(':')) {
      return null;
    }
    const separador = linha.indexOf(':');
    const campo = separador === -1 ? linha : linha.slice(0, separador);
    let valor = separador === -1 ? '' : linha.slice(separador + 1);
    if (valor.startsWith(' ')) {
      valor = valor.slice(1);
    }
    if (campo === 'event') {
      this.tipo = valor;
    } else if (campo === 'data') {
      this.dados.push(valor);
    }
    return null;
  }

  private despachar(): EventoSse | null {
    const evento = this.dados.length ? { tipo: this.tipo || 'message', dados: this.dados.join('\n') } : null;
    this.tipo = '';
    this.dados = [];
    return evento;
  }
}
