import { tempoRelativo } from './tempo-relativo';

describe('tempoRelativo', () => {
  // Datas locais, para o teste não depender do fuso da máquina.
  const agora = new Date(2026, 10, 11, 15, 30);
  const antes = (dias: number, horas = 0, minutos = 0) =>
    new Date(2026, 10, 11 - dias, 15 - horas, 30 - minutos).toISOString();

  it('mostra minutos e horas do mesmo jeito curto do MVP', () => {
    expect(tempoRelativo(antes(0, 0, 0), agora)).toBe('agora');
    expect(tempoRelativo(antes(0, 0, 12), agora)).toBe('12min');
    expect(tempoRelativo(antes(0, 1), agora)).toBe('1h');
    expect(tempoRelativo(antes(0, 23), agora)).toBe('23h');
  });

  it('passa para "ontem" e depois para dias', () => {
    expect(tempoRelativo(antes(1, 1), agora)).toBe('ontem');
    expect(tempoRelativo(antes(3), agora)).toBe('3d');
    expect(tempoRelativo(antes(6), agora)).toBe('6d');
  });

  it('depois de uma semana mostra a data, com o ano só quando não é o atual', () => {
    expect(tempoRelativo(new Date(2026, 2, 12, 9).toISOString(), agora)).toBe('12 mar');
    expect(tempoRelativo(new Date(2025, 2, 12, 9).toISOString(), agora)).toBe('12 mar 2025');
  });

  it('uma data no futuro, por relógio adiantado, vira "agora"', () => {
    expect(tempoRelativo(new Date(2026, 10, 11, 15, 35).toISOString(), agora)).toBe('agora');
  });
});
