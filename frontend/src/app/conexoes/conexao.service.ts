import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject, catchError, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { normalizarBusca } from '../bolhas/bolha.service';

/**
 * Como quem está logado enxerga a relação com outra pessoa. A recusa é discreta: quem pediu continua vendo
 * `PEDIDO_ENVIADO`, e quem recusou passa a ver `NENHUMA`.
 */
export type SituacaoConexao = 'NENHUMA' | 'PEDIDO_ENVIADO' | 'PEDIDO_RECEBIDO' | 'CONECTADOS';

/** O que o botão de conexão faz; cada ação tem o seu endereço na API. */
export type AcaoConexao = 'conectar' | 'recusar' | 'cancelarPedido' | 'desfazer';

/** Uma pessoa numa lista de conexões ou de pedidos. Não traz e-mail. */
export interface PessoaNaConexao {
  id: string;
  username: string;
  nomeCompleto: string;
  fotoUrl: string | null;
  /** Numa conexão, quando o pedido foi aceito; num pedido, quando foi feito. */
  desde: string;
}

/** `GET /api/conexoes/minhas`: as listas de quem está logado, as mais recentes primeiro. */
export interface MinhasConexoes {
  conexoes: PessoaNaConexao[];
  /** Pedidos ainda sem resposta. Os recusados não voltam. */
  recebidos: PessoaNaConexao[];
  /** Pedidos que ainda não viraram conexão, inclusive os recusados (a recusa é discreta). */
  enviados: PessoaNaConexao[];
}

interface Carregadas {
  usuarioId: string;
  dados: MinhasConexoes;
}

interface RespostaDaAcao {
  situacao: SituacaoConexao;
}

/**
 * Conexões de quem está logado. As listas ficam aqui, como as bolhas do menu em `BolhaService`, e alimentam o
 * botão de conexão de cada pessoa (no perfil, nos membros de uma bolha, nas listas) e o contador do menu.
 */
@Injectable({ providedIn: 'root' })
export class ConexaoService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly carregadas = signal<Carregadas | null>(null);
  private readonly recargas = new Subject<string>();

  /**
   * As listas de quem está logado. `null` enquanto não carregaram, e também quando as que estão guardadas são de
   * outra conta: depois de trocar de conta, nada da anterior aparece.
   */
  readonly minhas = computed(() => {
    const carregadas = this.carregadas();
    return carregadas && carregadas.usuarioId === this.auth.usuario()?.id ? carregadas.dados : null;
  });

  /** A situação com cada pessoa que tem algo com quem está logado. Quem não aparece aqui está em `NENHUMA`. */
  readonly situacoes = computed(() => {
    const minhas = this.minhas();
    return minhas ? situacoesPorPessoa(minhas) : null;
  });

  /** Pedidos esperando resposta: o número do item "Conexões" no menu. */
  readonly totalRecebidos = computed(() => this.minhas()?.recebidos.length ?? 0);

  constructor() {
    // A recarga mais nova vence: uma resposta atrasada não sobrescreve uma mais recente.
    this.recargas
      .pipe(
        switchMap((usuarioId) =>
          this.http.get<MinhasConexoes>('/api/conexoes/minhas').pipe(
            map((dados): Carregadas => ({ usuarioId, dados })),
            // Se falhar, fica o que já havia; a próxima recarga tenta de novo.
            catchError(() => of(null)),
          ),
        ),
      )
      .subscribe((carregadas) => {
        if (carregadas) {
          this.carregadas.set(carregadas);
        }
      });
  }

  /**
   * Busca as listas de novo: ao entrar, ao voltar para a aba do navegador, ao abrir telas com o botão de conexão e
   * depois de cada ação.
   */
  recarregarMinhas(): void {
    const usuarioId = this.auth.usuario()?.id;
    if (usuarioId) {
      this.recargas.next(usuarioId);
    }
  }

  /** Conexões aceitas de qualquer pessoa, as mais recentes primeiro. */
  doUsuario(usuarioId: string): Observable<PessoaNaConexao[]> {
    return this.http.get<PessoaNaConexao[]>(`/api/conexoes/usuario/${usuarioId}`);
  }

  total(usuarioId: string): Observable<number> {
    return this.http
      .get<{ total: number }>(`/api/conexoes/usuario/${usuarioId}/total`)
      .pipe(map((resposta) => resposta.total));
  }

  /** Pede a conexão, ou aceita, se a outra pessoa já tinha pedido. */
  conectar(usuarioId: string): Observable<SituacaoConexao> {
    return this.agir(this.http.post<RespostaDaAcao>(`/api/conexoes/com/${usuarioId}`, null));
  }

  recusar(usuarioId: string): Observable<SituacaoConexao> {
    return this.agir(this.http.post<RespostaDaAcao>(`/api/conexoes/com/${usuarioId}/recusa`, null));
  }

  cancelarPedido(usuarioId: string): Observable<SituacaoConexao> {
    return this.agir(this.http.delete<RespostaDaAcao>(`/api/conexoes/com/${usuarioId}/pedido`));
  }

  desfazer(usuarioId: string): Observable<SituacaoConexao> {
    return this.agir(this.http.delete<RespostaDaAcao>(`/api/conexoes/com/${usuarioId}`));
  }

  /** O servidor responde como a relação ficou; as listas são recarregadas para todas as telas se acertarem. */
  private agir(requisicao: Observable<RespostaDaAcao>): Observable<SituacaoConexao> {
    return requisicao.pipe(
      map((resposta) => resposta.situacao),
      tap(() => this.recarregarMinhas()),
    );
  }
}

/** A situação de cada pessoa, tirada das listas de quem está logado. */
export function situacoesPorPessoa(minhas: MinhasConexoes): ReadonlyMap<string, SituacaoConexao> {
  const situacoes = new Map<string, SituacaoConexao>();
  minhas.conexoes.forEach((pessoa) => situacoes.set(pessoa.id, 'CONECTADOS'));
  minhas.recebidos.forEach((pessoa) => situacoes.set(pessoa.id, 'PEDIDO_RECEBIDO'));
  minhas.enviados.forEach((pessoa) => situacoes.set(pessoa.id, 'PEDIDO_ENVIADO'));
  return situacoes;
}

/** Procura no nome e no username, sem diferenciar acento nem maiúscula; o `@` da frente é ignorado. */
export function casaComBuscaPessoa(
  pessoa: Pick<PessoaNaConexao, 'nomeCompleto' | 'username'>,
  busca: string,
): boolean {
  const termo = normalizarBusca(busca).replace(/^@/, '');
  if (!termo) {
    return true;
  }
  return [pessoa.nomeCompleto, pessoa.username].some((campo) => normalizarBusca(campo).includes(termo));
}

export function conexoesPorExtenso(total: number): string {
  return `${total} ${total === 1 ? 'conexão' : 'conexões'}`;
}

/** "Ana Beatriz Casiano" → "Ana", para os avisos. */
export function primeiroNome(nomeCompleto: string): string {
  return nomeCompleto.trim().split(/\s+/)[0] ?? '';
}

/** Quando a ação termina sem relação nenhuma entre as duas pessoas. */
const RECADO_SEM_RELACAO: Record<AcaoConexao, string> = {
  recusar: 'Pedido recusado',
  cancelarPedido: 'Pedido cancelado',
  desfazer: 'Conexão desfeita',
  // Só acontece se a outra pessoa sumir no meio do caminho.
  conectar: 'Não foi possível enviar o pedido. Tente de novo.',
};

/**
 * O aviso depois de uma ação. Ele diz como a relação ficou, que nem sempre é o que a ação pretendia: a tela podia
 * estar desatualizada (por exemplo, a outra pessoa aceitou o pedido que ia ser cancelado).
 */
export function recadoDaAcao(acao: AcaoConexao, situacao: SituacaoConexao, nome: string): string {
  switch (situacao) {
    case 'CONECTADOS':
      return acao === 'conectar'
        ? `Conexão feita com ${nome}`
        : `${nome} já tinha aceitado: a conexão está feita`;
    case 'PEDIDO_ENVIADO':
      return acao === 'conectar' ? `Pedido enviado para ${nome}` : `Seu pedido para ${nome} continua enviado`;
    case 'PEDIDO_RECEBIDO':
      return `${nome} enviou um pedido para você`;
    case 'NENHUMA':
      return RECADO_SEM_RELACAO[acao];
  }
}
