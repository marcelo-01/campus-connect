import {
  EMPTY,
  Observable,
  distinctUntilChanged,
  fromEvent,
  map,
  startWith,
  switchMap,
  timer,
} from 'rxjs';

/** A cada quanto tempo o feed pergunta se chegaram posts novos. */
export const INTERVALO_DAS_NOVAS = 30_000;

/** Se a aba está visível: o valor de agora e um novo a cada mudança. */
export function visibilidade(documento: Document): Observable<boolean> {
  return fromEvent(documento, 'visibilitychange').pipe(
    startWith(null),
    map(() => documento.visibilityState === 'visible'),
  );
}

/**
 * Os momentos em que o feed pergunta se chegaram posts novos: a cada `intervalo` com a aba visível, e
 * logo que ela volta a ficar visível. Com a aba escondida, não pergunta nada. A primeira pergunta espera
 * um intervalo inteiro, porque o feed acabou de carregar.
 */
export function verificacoes(
  visivel: Observable<boolean>,
  intervalo = INTERVALO_DAS_NOVAS,
): Observable<number> {
  return visivel.pipe(
    distinctUntilChanged(),
    switchMap((sim, vez) => (sim ? timer(vez === 0 ? intervalo : 0, intervalo) : EMPTY)),
  );
}
