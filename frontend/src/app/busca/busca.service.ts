import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PessoaPublica } from '../publicacoes/publicacao.service';

/** Uma página da busca de pessoas. */
export interface PaginaPessoas {
  itens: PessoaPublica[];
  /** Cursor da página seguinte; `null` na última. */
  proximo: string | null;
}

/** As abas da página de busca, na ordem em que aparecem. Sem `aba` na URL, vale a primeira. */
export type AbaBusca = 'posts' | 'pessoas' | 'bolhas';
export const ABAS_BUSCA: { valor: AbaBusca; rotulo: string }[] = [
  { valor: 'posts', rotulo: 'Posts' },
  { valor: 'pessoas', rotulo: 'Pessoas' },
  { valor: 'bolhas', rotulo: 'Bolhas' },
];

/** O backend recusa buscas mais longas. */
export const TAMANHO_BUSCA = 100;
/** Quantas pessoas a lista do campo de busca sugere. */
export const SUGESTOES_DE_PESSOAS = 5;

/**
 * A busca de pessoas, para a aba Pessoas e para as sugestões do campo da barra do topo. A de posts é um feed
 * (`EscopoFeed` do tipo `busca`), e a de bolhas filtra no navegador a lista que o Explorar já usa.
 */
@Injectable({ providedIn: 'root' })
export class BuscaService {
  private readonly http = inject(HttpClient);

  pessoas(termo: string, cursor: string | null = null, limite: number | null = null): Observable<PaginaPessoas> {
    return this.http.get<PaginaPessoas>('/api/usuarios/busca', {
      params: parametrosDaBusca(termo, cursor, limite),
    });
  }
}

export function parametrosDaBusca(termo: string, cursor: string | null, limite: number | null): HttpParams {
  let parametros = new HttpParams().set('q', termo.trim());
  if (cursor) {
    parametros = parametros.set('cursor', cursor);
  }
  if (limite) {
    parametros = parametros.set('limite', limite);
  }
  return parametros;
}

/** O endereço da página de busca. A aba Posts, que é a primeira, não vai na URL. */
export function enderecoDaBusca(termo: string, aba: AbaBusca = 'posts'): {
  caminho: string[];
  queryParams: { q: string | null; aba: AbaBusca | null };
} {
  const texto = termo.trim();
  return { caminho: ['/busca'], queryParams: { q: texto || null, aba: aba === 'posts' ? null : aba } };
}

/** A aba da URL, ou Posts quando ela não vem ou não existe. */
export function abaDaUrl(valor: string | null): AbaBusca {
  return ABAS_BUSCA.some((aba) => aba.valor === valor) ? (valor as AbaBusca) : 'posts';
}
