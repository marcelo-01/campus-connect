import {
  BolhaResumo,
  TipoBolha,
  caminhoCompleto,
  casaComBusca,
  exigeBolhaAcima,
  membrosPorExtenso,
  paisPermitidos,
  recuoPorNivel,
  siglaDaBolha,
  siglaExibida,
} from './bolha.service';

function bolha(nome: string, tipo: TipoBolha, campos: Partial<BolhaResumo> = {}): BolhaResumo {
  return {
    id: nome,
    nome,
    slug: nome.toLowerCase(),
    sigla: null,
    tipo,
    fotoUrl: null,
    descricao: null,
    bolhaPaiId: null,
    nivel: 0,
    totalMembros: 0,
    membro: false,
    ...campos,
  };
}

const ifba = bolha('IFBA', 'GERAL');
const campus = bolha('Campus Eunápolis', 'CAMPUS', { bolhaPaiId: 'IFBA', nivel: 1 });
const ads = bolha('ADS', 'CURSO', { bolhaPaiId: 'Campus Eunápolis', nivel: 2 });
const cinema = bolha('Cinema', 'COMUNIDADE');
const todas = [ifba, campus, ads, cinema];

describe('funções de bolha', () => {
  it('gera a sigla a partir das palavras principais do nome', () => {
    expect(siglaDaBolha('Campus Eunápolis')).toBe('CE');
    expect(siglaDaBolha('Cinema na sexta')).toBe('CS');
    expect(siglaDaBolha('ADS')).toBe('AD');
  });

  it('prefere a sigla cadastrada e corta no tamanho do círculo', () => {
    expect(siglaExibida({ nome: 'Instituto Federal', sigla: 'IFBA' }, 2)).toBe('IF');
    expect(siglaExibida({ nome: 'Instituto Federal', sigla: 'IFBA' }, 4)).toBe('IFBA');
    expect(siglaExibida({ nome: 'Campus Eunápolis', sigla: '  ' }, 4)).toBe('CE');
  });

  it('só oferece como bolha acima o que o backend aceita', () => {
    expect(paisPermitidos('GERAL', todas)).toEqual([]);
    expect(paisPermitidos('CAMPUS', todas)).toEqual([ifba]);
    expect(paisPermitidos('CURSO', todas)).toEqual([campus]);
    expect(paisPermitidos('COMUNIDADE', todas)).toEqual([ifba, campus, ads]);
  });

  it('exige bolha acima só para campus e curso', () => {
    expect(exigeBolhaAcima('CAMPUS')).toBe(true);
    expect(exigeBolhaAcima('CURSO')).toBe(true);
    expect(exigeBolhaAcima('GERAL')).toBe(false);
    expect(exigeBolhaAcima('COMUNIDADE')).toBe(false);
  });

  it('busca sem diferenciar acento nem maiúscula, no nome, na sigla e na descrição', () => {
    expect(casaComBusca(campus, 'eunapolis')).toBe(true);
    expect(casaComBusca(campus, '  EUNÁ ')).toBe(true);
    expect(casaComBusca(bolha('X', 'GERAL', { sigla: 'IFBA' }), 'ifb')).toBe(true);
    expect(casaComBusca(bolha('X', 'GERAL', { descricao: 'Robótica e drones' }), 'robotica')).toBe(
      true,
    );
    expect(casaComBusca(ads, 'cinema')).toBe(false);
    expect(casaComBusca(ads, '')).toBe(true);
  });

  it('mostra o caminho inteiro da bolha', () => {
    expect(caminhoCompleto(ads, todas)).toBe('IFBA › Campus Eunápolis › ADS');
    expect(caminhoCompleto(cinema, todas)).toBe('Cinema');
  });

  it('recua no menu conforme o nível, como o MVP', () => {
    expect([0, 1, 2, 3, 4].map(recuoPorNivel)).toEqual([
      '',
      'nested',
      'nested-2',
      'nested-3',
      'nested-3',
    ]);
  });

  it('escreve o total de membros no singular e no plural', () => {
    expect(membrosPorExtenso(1)).toBe('1 membro');
    expect(membrosPorExtenso(0)).toBe('0 membros');
  });
});
