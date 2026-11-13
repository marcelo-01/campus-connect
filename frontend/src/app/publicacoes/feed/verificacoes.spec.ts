import { BehaviorSubject, Subscription } from 'rxjs';
import { verificacoes } from './verificacoes';

describe('verificacoes', () => {
  let inscricao: Subscription | undefined;

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    inscricao?.unsubscribe();
    vi.useRealTimers();
  });

  function contar(visivel: BehaviorSubject<boolean>): () => number {
    let vezes = 0;
    inscricao = verificacoes(visivel, 1000).subscribe(() => vezes++);
    return () => vezes;
  }

  it('espera um intervalo inteiro depois de carregar e repete enquanto a aba está visível', () => {
    const vezes = contar(new BehaviorSubject(true));

    vi.advanceTimersByTime(999);
    expect(vezes()).toBe(0);
    vi.advanceTimersByTime(1);
    expect(vezes()).toBe(1);
    vi.advanceTimersByTime(2000);
    expect(vezes()).toBe(3);
  });

  it('para com a aba escondida e pergunta logo que ela volta', () => {
    const visivel = new BehaviorSubject(true);
    const vezes = contar(visivel);

    visivel.next(false);
    vi.advanceTimersByTime(5000);
    expect(vezes()).toBe(0);

    visivel.next(true);
    vi.advanceTimersByTime(0);
    expect(vezes()).toBe(1);
    vi.advanceTimersByTime(1000);
    expect(vezes()).toBe(2);
  });

  it('com a aba escondida desde o começo, pergunta assim que ela aparece', () => {
    const visivel = new BehaviorSubject(false);
    const vezes = contar(visivel);

    vi.advanceTimersByTime(5000);
    expect(vezes()).toBe(0);
    visivel.next(true);
    vi.advanceTimersByTime(0);
    expect(vezes()).toBe(1);
  });

  it('um aviso repetido de aba visível não reinicia a contagem', () => {
    const visivel = new BehaviorSubject(true);
    const vezes = contar(visivel);

    vi.advanceTimersByTime(600);
    visivel.next(true);
    vi.advanceTimersByTime(400);
    expect(vezes()).toBe(1);
  });
});
