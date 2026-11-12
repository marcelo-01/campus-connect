import { trechos } from './texto-publicacao';

describe('trechos do texto de um post', () => {
  it('deixa texto comum como está', () => {
    expect(trechos('Bom dia, campus!')).toEqual([{ tipo: 'texto', texto: 'Bom dia, campus!' }]);
    expect(trechos('')).toEqual([]);
  });

  it('acha links e tira a pontuação da frase do fim deles', () => {
    expect(trechos('Veja https://ifba.edu.br/eventos.')).toEqual([
      { tipo: 'texto', texto: 'Veja ' },
      { tipo: 'link', texto: 'https://ifba.edu.br/eventos', url: 'https://ifba.edu.br/eventos' },
      { tipo: 'texto', texto: '.' },
    ]);
    expect(trechos('(www.exemplo.com)')).toEqual([
      { tipo: 'texto', texto: '(' },
      { tipo: 'link', texto: 'www.exemplo.com', url: 'https://www.exemplo.com' },
      { tipo: 'texto', texto: ')' },
    ]);
    expect(trechos('https://pt.wikipedia.org/wiki/Java_(linguagem)')[0]).toEqual({
      tipo: 'link',
      texto: 'https://pt.wikipedia.org/wiki/Java_(linguagem)',
      url: 'https://pt.wikipedia.org/wiki/Java_(linguagem)',
    });
  });

  it('acha menções e hashtags no começo de palavra', () => {
    expect(trechos('@bia e @ana.bc, bora? #ADS #semana_academica')).toEqual([
      { tipo: 'mencao', texto: '@bia', username: 'bia' },
      { tipo: 'texto', texto: ' e ' },
      { tipo: 'mencao', texto: '@ana.bc', username: 'ana.bc' },
      { tipo: 'texto', texto: ', bora? ' },
      { tipo: 'hashtag', texto: '#ADS' },
      { tipo: 'texto', texto: ' ' },
      { tipo: 'hashtag', texto: '#semana_academica' },
    ]);
  });

  it('não confunde e-mail com menção nem tira o ponto do fim da frase do username', () => {
    expect(trechos('Escreva para ana@exemplo.com')).toEqual([
      { tipo: 'texto', texto: 'Escreva para ana@exemplo.com' },
    ]);
    expect(trechos('Valeu, @Bia.')).toEqual([
      { tipo: 'texto', texto: 'Valeu, ' },
      { tipo: 'mencao', texto: '@Bia', username: 'bia' },
      { tipo: 'texto', texto: '.' },
    ]);
    expect(trechos('@ab é curto demais')).toEqual([{ tipo: 'texto', texto: '@ab é curto demais' }]);
  });

  it('nunca transforma o texto em HTML', () => {
    expect(trechos('<img src=x onerror=alert(1)>')).toEqual([
      { tipo: 'texto', texto: '<img src=x onerror=alert(1)>' },
    ]);
  });
});
