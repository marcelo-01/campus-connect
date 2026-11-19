/** Como o anúncio cobra (`ModalidadePreco` no backend). */
export type ModalidadePreco = 'FIXO' | 'POR_HORA' | 'A_COMBINAR' | 'GRATUITO';

/** O preço como o card do MVP mostra: o valor grande e, ao lado, o `small` com o "/h". */
export interface PrecoExibido {
  valor: string;
  sufixo: string;
}

/** Só preço fixo e por hora levam um valor; "A combinar" e "Grátis" ficam sem. */
export function pedePreco(modalidade: ModalidadePreco | null | undefined): boolean {
  return modalidade === 'FIXO' || modalidade === 'POR_HORA';
}

/** "R$ 120", "R$ 1.250,50", "R$ 35" + "/h", "A combinar" ou "Grátis". */
export function precoPorExtenso(
  modalidade: ModalidadePreco | null | undefined,
  preco: number | null | undefined,
): PrecoExibido {
  if (modalidade === 'GRATUITO') {
    return { valor: 'Grátis', sufixo: '' };
  }
  if (!pedePreco(modalidade) || preco === null || preco === undefined) {
    return { valor: 'A combinar', sufixo: '' };
  }
  return { valor: `R$ ${valorEmReais(preco)}`, sufixo: modalidade === 'POR_HORA' ? '/h' : '' };
}

/** "120", "1.250,50": centavos só quando há centavos. */
export function valorEmReais(preco: number): string {
  const inteiro = Number.isInteger(preco);
  return preco.toLocaleString('pt-BR', {
    minimumFractionDigits: inteiro ? 0 : 2,
    maximumFractionDigits: 2,
  });
}

/** A média das notas com uma casa: "4,8". */
export function notaPorExtenso(media: number): string {
  return media.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/**
 * Capas de quem anuncia sem foto: o `.ph` do MVP, que mostrava o começo do título sobre um gradiente. Os
 * gradientes ficam no `styles.css` (`--capa-1` a `--capa-5`): tons claros da paleta (lavanda, tangerina, creme)
 * no tema claro e os mesmos matizes, fundos, no escuro, para a capa não saltar do preto.
 */
export const GRADIENTES_DA_CAPA = [
  'var(--capa-1)',
  'var(--capa-2)',
  'var(--capa-3)',
  'var(--capa-4)',
  'var(--capa-5)',
];

/** Sempre o mesmo gradiente para o mesmo anúncio (pelo id) ou, na prévia, para o mesmo título. */
export function gradienteDaCapa(semente: string): string {
  let soma = 0;
  for (const caractere of semente) {
    soma = (soma * 31 + caractere.charCodeAt(0)) >>> 0;
  }
  return GRADIENTES_DA_CAPA[soma % GRADIENTES_DA_CAPA.length];
}

/** As três primeiras palavras do título, como o `.ph` do MVP. */
export function textoDaCapa(titulo: string): string {
  return titulo.trim().split(/\s+/).slice(0, 3).join(' ');
}
