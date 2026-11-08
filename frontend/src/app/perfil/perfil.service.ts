import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Perfil público (`GET /api/usuarios/{username}/perfil`). Não traz e-mail. */
export interface Perfil {
  id: string;
  username: string;
  nomeCompleto: string;
  bio: string | null;
  /** Sempre com `https://` ou `http://` na frente. */
  link: string | null;
  habilidades: string[];
  fotoUrl: string | null;
  capaUrl: string | null;
  membroDesde: string;
  /** Se quem está vendo é o dono do perfil. */
  proprio: boolean;
}

export interface AlteracaoPerfil {
  nomeCompleto: string;
  username: string;
  bio: string;
  link: string;
  habilidades: string[];
}

export type TipoBolha = 'INSTITUICAO' | 'CAMPUS' | 'CURSO' | 'COMUNIDADE';

export interface BolhaResumo {
  id: string;
  nome: string;
  slug: string;
  tipo: TipoBolha;
  totalMembros: number;
}

/** Limites conferidos de novo pelo backend. */
export const TAMANHO_MAXIMO_IMAGEM = 5 * 1024 * 1024;
export const TIPOS_DE_IMAGEM = ['image/jpeg', 'image/png'];

@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);

  buscar(username: string): Observable<Perfil> {
    return this.http.get<Perfil>(`/api/usuarios/${encodeURIComponent(username)}/perfil`);
  }

  atualizar(alteracao: AlteracaoPerfil): Observable<Perfil> {
    return this.http.put<Perfil>('/api/usuarios/me/perfil', alteracao);
  }

  trocarFoto(arquivo: File): Observable<Perfil> {
    return this.http.post<Perfil>('/api/usuarios/me/foto', comArquivo(arquivo));
  }

  trocarCapa(arquivo: File): Observable<Perfil> {
    return this.http.post<Perfil>('/api/usuarios/me/capa', comArquivo(arquivo));
  }

  bolhasDoUsuario(usuarioId: string): Observable<BolhaResumo[]> {
    return this.http.get<BolhaResumo[]>(`/api/bolhas/usuario/${usuarioId}`);
  }

  totalDeConexoes(usuarioId: string): Observable<number> {
    return this.http
      .get<{ total: number }>(`/api/conexoes/usuario/${usuarioId}/total`)
      .pipe(map((resposta) => resposta.total));
  }
}

function comArquivo(arquivo: File): FormData {
  const formulario = new FormData();
  formulario.append('arquivo', arquivo);
  return formulario;
}

/** Mensagem de erro para mostrar antes de enviar, ou `null` se o arquivo pode seguir. */
export function problemaNaImagem(arquivo: File): string | null {
  if (!TIPOS_DE_IMAGEM.includes(arquivo.type)) {
    return 'Escolha uma imagem JPG ou PNG.';
  }
  if (arquivo.size > TAMANHO_MAXIMO_IMAGEM) {
    return 'A imagem pode ter no máximo 5 MB.';
  }
  return null;
}

/** "https://github.com/ana/" → "github.com/ana", como o MVP mostra o link. */
export function linkSemEsquema(link: string): string {
  return link.replace(/^https?:\/\//i, '').replace(/\/$/, '');
}

/** "2024-02-10T…" → "fev 2024", como em "@marcelo.h · entrou em fev 2024" no MVP. */
export function mesEAno(dataIso: string): string {
  const data = new Date(dataIso);
  const mes = data.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '');
  return `${mes} ${data.getFullYear()}`;
}

/** Cor e sigla da bolha. O MVP pinta cada nível da hierarquia num tom da marca; aqui vale o mesmo. */
const CORES_POR_TIPO: Record<TipoBolha, string> = {
  INSTITUICAO: 'oklch(0.34 0.13 282)',
  CAMPUS: 'oklch(0.50 0.18 286)',
  CURSO: 'oklch(0.62 0.21 290)',
  COMUNIDADE: 'oklch(0.70 0.16 58)',
};

export function corDaBolha(tipo: TipoBolha): string {
  return CORES_POR_TIPO[tipo];
}

export function siglaDaBolha(nome: string): string {
  const palavras = nome.split(/\s+/).filter((p) => p.length > 2 || /^[A-Z]/.test(p));
  const letras = palavras.length > 1 ? palavras[0][0] + palavras[1][0] : nome.slice(0, 2);
  return letras.toUpperCase();
}

/** Recuo no menu lateral, como o MVP aninha campus dentro da instituição e curso dentro do campus. */
export function recuoDaBolha(tipo: TipoBolha): '' | 'nested' | 'nested-2' {
  return tipo === 'CAMPUS' ? 'nested' : tipo === 'CURSO' ? 'nested-2' : '';
}
