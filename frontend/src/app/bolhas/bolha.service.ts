import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { comArquivo } from '../shared/imagem';

export type TipoBolha = 'GERAL' | 'CAMPUS' | 'CURSO' | 'COMUNIDADE';

/** Bolha numa lista (`GET /api/bolhas`, `GET /api/bolhas/usuario/{id}`). */
export interface BolhaResumo {
  id: string;
  nome: string;
  slug: string;
  sigla: string | null;
  tipo: TipoBolha;
  fotoUrl: string | null;
  descricao: string | null;
  /** Bolha acima, ou `null` se esta fica no topo. */
  bolhaPaiId: string | null;
  /** Profundidade na árvore: 0 no topo. */
  nivel: number;
  totalMembros: number;
  /** Se quem está vendo participa. */
  membro: boolean;
}

/** Página de uma bolha (`GET /api/bolhas/{slug}`). */
export interface BolhaDetalhe {
  id: string;
  nome: string;
  slug: string;
  sigla: string | null;
  tipo: TipoBolha;
  fotoUrl: string | null;
  descricao: string | null;
  endereco: string | null;
  totalMembros: number;
  membro: boolean;
  criadaEm: string;
  /** As bolhas acima, do topo até a mãe. */
  caminho: BolhaResumo[];
  filhas: BolhaResumo[];
}

export interface MembroDaBolha {
  id: string;
  username: string;
  nomeCompleto: string;
  fotoUrl: string | null;
  papel: 'MEMBRO' | 'MODERADOR' | 'ADMIN';
  entrouEm: string;
}

export interface NovaBolha {
  nome: string;
  sigla: string;
  tipo: TipoBolha;
  bolhaPaiId: string | null;
  descricao: string;
  endereco: string;
}

/** Tipo e bolha acima não mudam depois de criados. */
export interface AlteracaoBolha {
  nome: string;
  sigla: string;
  descricao: string;
  endereco: string;
}

/** O que a marca da bolha (círculo com foto ou sigla) precisa saber. */
export type MarcaDaBolha = Pick<BolhaResumo, 'nome' | 'sigla' | 'tipo' | 'fotoUrl'>;

@Injectable({ providedIn: 'root' })
export class BolhaService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly minhasAtuais = signal<BolhaResumo[] | null>(null);

  /** Bolhas de quem está logado, para o menu lateral. `null` enquanto ainda não carregou. */
  readonly minhas = this.minhasAtuais.asReadonly();

  /** Chamado ao entrar e depois de cada ação que muda a lista: entrar, sair, editar, trocar foto, excluir. */
  recarregarMinhas(): void {
    const usuario = this.auth.usuario();
    if (!usuario) {
      this.minhasAtuais.set(null);
      return;
    }
    this.doUsuario(usuario.id).subscribe({
      next: (bolhas) => this.minhasAtuais.set(bolhas),
      error: () => this.minhasAtuais.set([]),
    });
  }

  todas(): Observable<BolhaResumo[]> {
    return this.http.get<BolhaResumo[]>('/api/bolhas');
  }

  doUsuario(usuarioId: string): Observable<BolhaResumo[]> {
    return this.http.get<BolhaResumo[]>(`/api/bolhas/usuario/${usuarioId}`);
  }

  buscar(slug: string): Observable<BolhaDetalhe> {
    return this.http.get<BolhaDetalhe>(`/api/bolhas/${encodeURIComponent(slug)}`);
  }

  membros(bolhaId: string): Observable<MembroDaBolha[]> {
    return this.http.get<MembroDaBolha[]>(`/api/bolhas/${bolhaId}/membros`);
  }

  /** Entra também nas bolhas acima. */
  entrar(bolhaId: string): Observable<void> {
    return this.http
      .post<void>(`/api/bolhas/${bolhaId}/participacao`, null)
      .pipe(tap(() => this.recarregarMinhas()));
  }

  /** Sai também das bolhas de dentro. */
  sair(bolhaId: string): Observable<void> {
    return this.http
      .delete<void>(`/api/bolhas/${bolhaId}/participacao`)
      .pipe(tap(() => this.recarregarMinhas()));
  }

  criar(nova: NovaBolha): Observable<BolhaDetalhe> {
    return this.http.post<BolhaDetalhe>('/api/bolhas', nova);
  }

  atualizar(bolhaId: string, alteracao: AlteracaoBolha): Observable<BolhaDetalhe> {
    return this.http
      .put<BolhaDetalhe>(`/api/bolhas/${bolhaId}`, alteracao)
      .pipe(tap(() => this.recarregarMinhas()));
  }

  trocarFoto(bolhaId: string, arquivo: File): Observable<BolhaDetalhe> {
    return this.http
      .post<BolhaDetalhe>(`/api/bolhas/${bolhaId}/foto`, comArquivo(arquivo))
      .pipe(tap(() => this.recarregarMinhas()));
  }

  excluir(bolhaId: string): Observable<void> {
    return this.http
      .delete<void>(`/api/bolhas/${bolhaId}`)
      .pipe(tap(() => this.recarregarMinhas()));
  }
}

/** O MVP pinta cada nível da hierarquia num tom da marca; comunidades ficam na tangerina. */
const CORES_POR_TIPO: Record<TipoBolha, string> = {
  GERAL: 'oklch(0.34 0.13 282)',
  CAMPUS: 'oklch(0.50 0.18 286)',
  CURSO: 'oklch(0.62 0.21 290)',
  COMUNIDADE: 'oklch(0.70 0.16 58)',
};

const ROTULOS: Record<TipoBolha, string> = {
  GERAL: 'Geral',
  CAMPUS: 'Campus',
  CURSO: 'Curso',
  COMUNIDADE: 'Comunidade',
};

export const TIPOS_DE_BOLHA: TipoBolha[] = ['GERAL', 'CAMPUS', 'CURSO', 'COMUNIDADE'];

export function corDaBolha(tipo: TipoBolha): string {
  return CORES_POR_TIPO[tipo];
}

export function rotuloDoTipo(tipo: TipoBolha): string {
  return ROTULOS[tipo];
}

/** Sigla tirada do nome, para bolhas cadastradas sem sigla: "Campus Eunápolis" → "CE". */
export function siglaDaBolha(nome: string): string {
  const palavras = nome.split(/\s+/).filter((p) => p.length > 2 || /^[A-Z]/.test(p));
  const letras = palavras.length > 1 ? palavras[0][0] + palavras[1][0] : nome.slice(0, 2);
  return letras.toUpperCase();
}

/**
 * Texto do círculo da bolha. O círculo pequeno do MVP (22 px) só comporta duas letras, como o "IF" do
 * IFBA; o grande mostra até quatro.
 */
export function siglaExibida(bolha: Pick<MarcaDaBolha, 'nome' | 'sigla'>, maximo: 2 | 4): string {
  const sigla = bolha.sigla?.trim() || siglaDaBolha(bolha.nome);
  return sigla.slice(0, maximo);
}

/** Recuo no menu lateral, como o MVP aninha campus dentro da geral e curso dentro do campus. */
export function recuoPorNivel(nivel: number): '' | 'nested' | 'nested-2' | 'nested-3' {
  return nivel <= 0 ? '' : nivel === 1 ? 'nested' : nivel === 2 ? 'nested-2' : 'nested-3';
}

/** Campus e curso precisam de bolha acima; geral não pode ter; comunidade escolhe. */
export function exigeBolhaAcima(tipo: TipoBolha): boolean {
  return tipo === 'CAMPUS' || tipo === 'CURSO';
}

/** Onde uma bolha nova do tipo pode ficar. Espelha `TipoBolha.podeFicarDentroDe` do backend. */
export function paisPermitidos(tipo: TipoBolha, bolhas: BolhaResumo[]): BolhaResumo[] {
  switch (tipo) {
    case 'GERAL':
      return [];
    case 'CAMPUS':
      return bolhas.filter((b) => b.tipo === 'GERAL');
    case 'CURSO':
      return bolhas.filter((b) => b.tipo === 'CAMPUS');
    case 'COMUNIDADE':
      return bolhas.filter((b) => b.tipo !== 'COMUNIDADE');
  }
}

/** Minúsculas e sem acento: "Eunápolis" e "eunapolis" casam na busca. */
export function normalizarBusca(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

/** Procura no nome, na sigla e na descrição. Busca vazia casa com tudo. */
export function casaComBusca(bolha: BolhaResumo, busca: string): boolean {
  const termo = normalizarBusca(busca);
  if (!termo) {
    return true;
  }
  return [bolha.nome, bolha.sigla, bolha.descricao].some(
    (campo) => campo && normalizarBusca(campo).includes(termo),
  );
}

/** "IFBA › Campus Eunápolis › ADS", seguindo `bolhaPaiId` dentro da lista. */
export function caminhoCompleto(bolha: BolhaResumo, todas: BolhaResumo[]): string {
  const porId = new Map(todas.map((b) => [b.id, b]));
  const nomes = [bolha.nome];
  for (let pai = bolha.bolhaPaiId ? porId.get(bolha.bolhaPaiId) : undefined; pai;) {
    nomes.unshift(pai.nome);
    pai = pai.bolhaPaiId ? porId.get(pai.bolhaPaiId) : undefined;
  }
  return nomes.join(' › ');
}

export function membrosPorExtenso(total: number): string {
  return `${total} ${total === 1 ? 'membro' : 'membros'}`;
}
