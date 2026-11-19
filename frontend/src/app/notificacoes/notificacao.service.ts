import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject, catchError, map, merge, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { AoVivoService } from '../shared/aovivo/aovivo.service';

/** O evento ao vivo que avisa que o contador mudou. Vem sem dados: o número é pedido de novo. */
export const EVENTO_NOTIFICACOES = 'notificacoes';

export type TipoNotificacao =
  | 'SOLICITACAO_CONEXAO'
  | 'CONEXAO_ACEITA'
  | 'NOVO_COMENTARIO'
  | 'RESPOSTA_COMENTARIO'
  | 'CURTIDA_PUBLICACAO'
  | 'CURTIDA_COMENTARIO'
  | 'REPUBLICACAO'
  | 'AVALIACAO_ANUNCIO';

/** Uma notificação. As curtidas e as republicações do mesmo alvo vêm juntas, numa só. */
export interface Notificacao {
  /** Estável entre páginas e recargas. */
  id: string;
  tipo: TipoNotificacao;
  /** Quando chegou a mais nova do grupo. */
  em: string;
  /** Se ainda não tinha sido lida quando a página veio. */
  nova: boolean;
  /** Quem fez, da mais recente para a mais antiga, até 3. */
  pessoas: PessoaPublica[];
  /** Quantas pessoas fizeram, contando as que não estão em `pessoas`. */
  total: number;
  publicacaoId: string | null;
  anuncioId: string | null;
  /** O comentário; o começo da publicação curtida ou republicada; o título do anúncio avaliado. */
  trecho: string | null;
}

export interface PaginaNotificacoes {
  itens: Notificacao[];
  /** Onde a próxima página começa; `null` na última. */
  proximo: string | null;
  /** Só na primeira página: quando chegou a mais nova, para marcar como lidas até ela. */
  maisNovaEm: string | null;
}

interface Contagem {
  usuarioId: string;
  total: number;
}

/**
 * As notificações e o número de não lidas, que aparece no sino da barra do topo e no item "Notificações" do menu.
 * O número se mantém em dia pelos eventos ao vivo.
 */
@Injectable({ providedIn: 'root' })
export class NotificacaoService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly aoVivo = inject(AoVivoService);

  private readonly contagem = signal<Contagem | null>(null);
  private readonly recargas = new Subject<string>();

  /** Quantas ainda não foram lidas. Zero enquanto não carregou ou se a contagem é de outra conta. */
  readonly naoLidas = computed(() => {
    const contagem = this.contagem();
    return contagem && contagem.usuarioId === this.auth.usuario()?.id ? contagem.total : 0;
  });

  constructor() {
    // A recarga mais nova vence: uma resposta atrasada não sobrescreve uma mais recente.
    this.recargas
      .pipe(
        switchMap((usuarioId) =>
          this.http.get<{ total: number }>('/api/notificacoes/nao-lidas').pipe(
            map((resposta): Contagem => ({ usuarioId, total: resposta.total })),
            catchError(() => of(null)),
          ),
        ),
      )
      .subscribe((contagem) => {
        if (contagem) {
          this.contagem.set(contagem);
        }
      });

    merge(this.aoVivo.sincronizar, this.aoVivo.eventos(EVENTO_NOTIFICACOES)).subscribe(() =>
      this.recarregarNaoLidas(),
    );
  }

  recarregarNaoLidas(): void {
    const usuarioId = this.auth.usuario()?.id;
    if (usuarioId) {
      this.recargas.next(usuarioId);
    }
  }

  /** Uma página, da mais nova para a mais antiga. */
  listar(cursor: string | null = null): Observable<PaginaNotificacoes> {
    const params = cursor ? new HttpParams().set('cursor', cursor) : undefined;
    return this.http.get<PaginaNotificacoes>('/api/notificacoes', { params });
  }

  /**
   * Marca como lidas as que chegaram até `ate`, o `maisNovaEm` da primeira página. O contador fica com as que
   * chegaram enquanto a página carregava.
   */
  marcarLidas(ate: string): Observable<number> {
    const usuarioId = this.auth.usuario()?.id;
    return this.http.post<{ total: number }>('/api/notificacoes/leitura', { ate }).pipe(
      map((resposta) => resposta.total),
      tap((total) => {
        if (usuarioId && usuarioId === this.auth.usuario()?.id) {
          this.contagem.set({ usuarioId, total });
        }
      }),
    );
  }
}

// ------------------------------------------------------------ funções

/** Quem aparece no texto: até dois nomes e quantas pessoas mais. */
export interface Autores {
  nomes: string[];
  outros: number;
}

export function autores(notificacao: Notificacao): Autores {
  const nomes = notificacao.pessoas.slice(0, 2).map((pessoa) => pessoa.nomeCompleto);
  return { nomes, outros: Math.max(0, notificacao.total - nomes.length) };
}

/** Um pedaço do texto de quem fez. Os nomes vão em negrito. */
export interface Parte {
  texto: string;
  nome: boolean;
}

/** "Ana", "Ana e Bia", "Ana e mais 1 pessoa", "Ana, Bia e mais 3 pessoas", em pedaços. */
export function quemFez({ nomes, outros }: Autores): Parte[] {
  const itens: Parte[] = nomes.map((texto) => ({ texto, nome: true }));
  if (outros) {
    itens.push({ texto: `mais ${outros} ${outros === 1 ? 'pessoa' : 'pessoas'}`, nome: false });
  }
  return itens.flatMap((item, indice) => {
    if (indice === 0) {
      return [item];
    }
    const separador = indice === itens.length - 1 ? ' e ' : ', ';
    return [{ texto: separador, nome: false }, item];
  });
}

/** O que aconteceu, depois de quem fez, no singular ou no plural. */
export function acao(notificacao: Notificacao): string {
  const varias = notificacao.total > 1;
  switch (notificacao.tipo) {
    case 'SOLICITACAO_CONEXAO':
      return 'quer se conectar com você';
    case 'CONEXAO_ACEITA':
      return 'aceitou seu pedido de conexão';
    case 'NOVO_COMENTARIO':
      return 'comentou na sua publicação';
    case 'RESPOSTA_COMENTARIO':
      return 'respondeu ao seu comentário';
    case 'CURTIDA_PUBLICACAO':
      return varias ? 'curtiram sua publicação' : 'curtiu sua publicação';
    case 'CURTIDA_COMENTARIO':
      return varias ? 'curtiram seu comentário' : 'curtiu seu comentário';
    case 'REPUBLICACAO':
      return varias ? 'republicaram sua publicação' : 'republicou sua publicação';
    case 'AVALIACAO_ANUNCIO':
      return 'avaliou seu anúncio';
  }
}

/** Para onde a notificação leva, como os comandos de um `routerLink`, e os parâmetros da URL, se houver. */
export function destino(notificacao: Notificacao): { link: string[]; query?: Record<string, string> } {
  switch (notificacao.tipo) {
    case 'SOLICITACAO_CONEXAO':
      return { link: ['/conexoes'], query: { aba: 'recebidos' } };
    case 'CONEXAO_ACEITA':
      return { link: ['/perfil', notificacao.pessoas[0]?.username ?? ''] };
    case 'AVALIACAO_ANUNCIO':
      return { link: ['/marketplace', notificacao.anuncioId ?? ''] };
    default:
      return { link: ['/publicacoes', notificacao.publicacaoId ?? ''] };
  }
}
