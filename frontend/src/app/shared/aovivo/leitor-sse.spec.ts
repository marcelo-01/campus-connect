import { EventoSse, LeitorSse } from './leitor-sse';

const FLUXO =
  'event:pronto\ndata:{}\n\n' +
  ': pulso\n\n' +
  'event:mensagem\ndata:{"conteudo":"Olá, ação!"}\n\n' +
  'event: leitura\r\ndata: {"a":1}\r\n\r\n' +
  'data:linha 1\ndata:linha 2\n\n';

const ESPERADOS: EventoSse[] = [
  { tipo: 'pronto', dados: '{}' },
  { tipo: 'mensagem', dados: '{"conteudo":"Olá, ação!"}' },
  { tipo: 'leitura', dados: '{"a":1}' },
  { tipo: 'message', dados: 'linha 1\nlinha 2' },
];

describe('LeitorSse', () => {
  it('lê eventos, comentários, \\r\\n e várias linhas de data', () => {
    expect(new LeitorSse().lerTexto(FLUXO)).toEqual(ESPERADOS);
  });

  it('dá o mesmo resultado com o texto cortado em qualquer posição', () => {
    for (let corte = 1; corte < FLUXO.length; corte++) {
      const leitor = new LeitorSse();
      const eventos = [...leitor.lerTexto(FLUXO.slice(0, corte)), ...leitor.lerTexto(FLUXO.slice(corte))];
      expect(eventos, `corte em ${corte}`).toEqual(ESPERADOS);
    }
  });

  it('dá o mesmo resultado com os bytes cortados no meio de um caractere acentuado', () => {
    const bytes = new TextEncoder().encode(FLUXO);
    for (let corte = 1; corte < bytes.length; corte++) {
      const leitor = new LeitorSse();
      const eventos = [...leitor.ler(bytes.slice(0, corte)), ...leitor.ler(bytes.slice(corte))];
      expect(eventos, `corte no byte ${corte}`).toEqual(ESPERADOS);
    }
  });

  it('só entrega o evento quando chega a linha em branco', () => {
    const leitor = new LeitorSse();

    expect(leitor.lerTexto('event:mensagem\ndata:{}\n')).toEqual([]);
    expect(leitor.lerTexto('\n')).toEqual([{ tipo: 'mensagem', dados: '{}' }]);
  });

  it('ignora um evento sem data', () => {
    expect(new LeitorSse().lerTexto('event:vazio\n\n')).toEqual([]);
  });
});
