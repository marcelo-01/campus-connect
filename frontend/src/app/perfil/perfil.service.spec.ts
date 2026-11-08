import { iniciais } from '../shared/avatar';
import { linkSemEsquema, mesEAno, problemaNaImagem, siglaDaBolha } from './perfil.service';

describe('funções do perfil', () => {
  it('mostra o link sem https:// e sem barra no fim, como o MVP', () => {
    expect(linkSemEsquema('https://github.com/ana/')).toBe('github.com/ana');
    expect(linkSemEsquema('http://ana.dev')).toBe('ana.dev');
  });

  it('formata a data de entrada como "fev 2024"', () => {
    expect(mesEAno('2024-02-10T12:00:00Z')).toBe('fev 2024');
  });

  it('usa a primeira letra do primeiro e do último nome', () => {
    expect(iniciais('Marcelo Henrique Noronha Brito')).toBe('MB');
    expect(iniciais('Ana')).toBe('A');
    expect(iniciais('')).toBe('');
  });

  it('gera a sigla da bolha a partir das palavras principais', () => {
    expect(siglaDaBolha('Campus Eunápolis')).toBe('CE');
    expect(siglaDaBolha('Cinema na sexta')).toBe('CS');
    expect(siglaDaBolha('ADS')).toBe('AD');
  });

  it('confere tipo e tamanho da imagem antes de enviar', () => {
    const jpg = new File(['x'], 'foto.jpg', { type: 'image/jpeg' });
    const gif = new File(['x'], 'foto.gif', { type: 'image/gif' });
    const grande = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.png', { type: 'image/png' });

    expect(problemaNaImagem(jpg)).toBeNull();
    expect(problemaNaImagem(gif)).toBe('Escolha uma imagem JPG ou PNG.');
    expect(problemaNaImagem(grande)).toBe('A imagem pode ter no máximo 5 MB.');
  });
});
