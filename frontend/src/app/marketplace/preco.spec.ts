import {
  GRADIENTES_DA_CAPA,
  gradienteDaCapa,
  notaPorExtenso,
  pedePreco,
  precoPorExtenso,
  textoDaCapa,
  valorEmReais,
} from './preco';

describe('preço e capa dos anúncios', () => {
  it('mostra o preço como o card do MVP, com o "/h" à parte', () => {
    expect(precoPorExtenso('FIXO', 120)).toEqual({ valor: 'R$ 120', sufixo: '' });
    expect(precoPorExtenso('FIXO', 1250.5)).toEqual({ valor: 'R$ 1.250,50', sufixo: '' });
    expect(precoPorExtenso('POR_HORA', 35)).toEqual({ valor: 'R$ 35', sufixo: '/h' });
    expect(precoPorExtenso('A_COMBINAR', null)).toEqual({ valor: 'A combinar', sufixo: '' });
    expect(precoPorExtenso('GRATUITO', null)).toEqual({ valor: 'Grátis', sufixo: '' });
    expect(precoPorExtenso(null, null).valor).toBe('A combinar');
  });

  it('só pede preço em preço fixo e por hora', () => {
    expect(pedePreco('FIXO')).toBe(true);
    expect(pedePreco('POR_HORA')).toBe(true);
    expect(pedePreco('A_COMBINAR')).toBe(false);
    expect(pedePreco('GRATUITO')).toBe(false);
  });

  it('escreve os centavos só quando há centavos', () => {
    expect(valorEmReais(80)).toBe('80');
    expect(valorEmReais(0.5)).toBe('0,50');
    expect(valorEmReais(1000000)).toBe('1.000.000');
  });

  it('escreve a nota com uma casa', () => {
    expect(notaPorExtenso(4.8333)).toBe('4,8');
    expect(notaPorExtenso(5)).toBe('5,0');
  });

  it('escolhe sempre o mesmo gradiente para a mesma semente', () => {
    const gradiente = gradienteDaCapa('3f1c9a52-0000-4000-8000-000000000001');
    expect(GRADIENTES_DA_CAPA).toContain(gradiente);
    expect(gradienteDaCapa('3f1c9a52-0000-4000-8000-000000000001')).toBe(gradiente);
    const usados = new Set(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'].map(gradienteDaCapa));
    expect(usados.size).toBeGreaterThan(1);
  });

  it('põe na capa as três primeiras palavras do título', () => {
    expect(textoDaCapa('  Design de logo + identidade visual ')).toBe('Design de logo');
    expect(textoDaCapa('Fone')).toBe('Fone');
  });
});
