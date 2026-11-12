const MINUTO = 60_000;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;

/**
 * Há quanto tempo, no jeito curto do MVP: "agora", "12min", "1h", "ontem", "3d". Depois de uma semana vira
 * a data ("12 mar"), com o ano quando não é o atual ("12 mar 2025").
 */
export function tempoRelativo(dataIso: string, agora: Date = new Date()): string {
  const data = new Date(dataIso);
  const passou = Math.max(0, agora.getTime() - data.getTime());
  if (passou < MINUTO) {
    return 'agora';
  }
  if (passou < HORA) {
    return `${Math.floor(passou / MINUTO)}min`;
  }
  if (passou < DIA) {
    return `${Math.floor(passou / HORA)}h`;
  }
  const dias = diasDeCalendario(data, agora);
  if (dias === 1) {
    return 'ontem';
  }
  if (dias < 7) {
    return `${dias}d`;
  }
  const dia = data.getDate();
  const mes = data.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '');
  return data.getFullYear() === agora.getFullYear() ? `${dia} ${mes}` : `${dia} ${mes} ${data.getFullYear()}`;
}

/** "11 de novembro de 2026 às 14:05", para o `title` de quem passa o mouse no tempo. */
export function dataCompleta(dataIso: string): string {
  const data = new Date(dataIso);
  const dia = data.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });
  const hora = data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  return `${dia} às ${hora}`;
}

/** Quantas viradas de dia (meia-noite local) há entre as duas datas. */
function diasDeCalendario(antes: Date, depois: Date): number {
  const inicio = new Date(antes.getFullYear(), antes.getMonth(), antes.getDate());
  const fim = new Date(depois.getFullYear(), depois.getMonth(), depois.getDate());
  return Math.round((fim.getTime() - inicio.getTime()) / DIA);
}
