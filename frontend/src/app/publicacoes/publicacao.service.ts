import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BolhaResumo } from '../bolhas/bolha.service';
import { TAMANHO_MAXIMO_IMAGEM, TIPOS_DE_IMAGEM } from '../shared/imagem';

/** O que qualquer pessoa logada vê de outra conta. */
export interface PessoaPublica {
  id: string;
  username: string;
  nomeCompleto: string;
  fotoUrl: string | null;
}

export interface Midia {
  tipo: 'IMAGEM' | 'VIDEO';
  url: string;
  mimeType: string | null;
}

/** Sem prévia, só `url` vem preenchido e o card mostra o domínio. */
export interface LinkAnexado {
  url: string;
  titulo: string | null;
  descricao: string | null;
  site: string | null;
  imagemUrl: string | null;
}

/** Uma publicação vista por quem está logado (`PublicacaoVista` no backend). */
export interface Publicacao {
  id: string;
  autor: PessoaPublica;
  /** A bolha em que o post foi feito; pode ser uma acima da que a pessoa está vendo. */
  bolha: BolhaResumo;
  conteudo: string;
  /** Até 4 imagens ou 1 vídeo. */
  midias: Midia[];
  link: LinkAnexado | null;
  criadoEm: string;
  editadoEm: string | null;
  curtidas: number;
  comentarios: number;
  republicacoes: number;
  curtida: boolean;
  salva: boolean;
  republicada: boolean;
  /** Se quem vê é membro da bolha do post e pode curtir, comentar e republicar. */
  podeInteragir: boolean;
  podeEditar: boolean;
  podeExcluir: boolean;
}

export interface ItemFeed {
  publicacao: Publicacao;
  /** Quem republicou, no feed de um perfil. */
  republicadaPor: PessoaPublica | null;
  em: string;
}

export interface PaginaFeed {
  itens: ItemFeed[];
  /** Cursor da página seguinte; `null` na última. */
  proximo: string | null;
  /**
   * Marca do post mais novo, para perguntar depois se chegaram posts novos. Só vem na primeira página do
   * Início e da bolha, e nunca no "Em alta", que não é por data.
   */
  topo: string | null;
}

/** O aviso de posts novos no topo do feed. */
export interface NovasPublicacoes {
  /** Quantos posts novos há, contados até 20. */
  total: number;
  /** Se passou de 20. */
  mais: boolean;
  /** Quem publicou, até 3, de quem publicou por último para quem publicou antes. */
  autores: PessoaPublica[];
}

export interface Comentario {
  id: string;
  /** `null` quando o comentário foi removido e só ficou no lugar por causa das respostas. */
  autor: PessoaPublica | null;
  conteudo: string | null;
  criadoEm: string;
  curtidas: number;
  curtido: boolean;
  removido: boolean;
  podeExcluir: boolean;
  /** Um nível só: as respostas vêm sempre sem respostas. */
  respostas: Comentario[];
}

/** Os chips do feed do MVP. */
export type OrdemFeed = 'recentes' | 'alta' | 'midia';
export type OrdemComentarios = 'relevantes' | 'recentes';

/** De onde vêm os posts de um feed. */
export type EscopoFeed =
  /** As bolhas de quem está logado; com `bolhaId`, só os posts feitos nela. */
  | { tipo: 'inicio'; bolhaId: string | null }
  /** A bolha e as de cima: o que é publicado numa bolha desce para as de dentro. */
  | { tipo: 'bolha'; bolhaId: string }
  /** Os posts de uma pessoa e os que ela republicou. */
  | { tipo: 'usuario'; usuarioId: string }
  | { tipo: 'salvas' };

export interface NovaPublicacao {
  bolhaId: string;
  conteudo: string;
  link: string | null;
  imagens: File[];
  video: File | null;
}

/** Limites conferidos de novo pelo backend. */
export const MAXIMO_IMAGENS = 4;
export const TAMANHO_MAXIMO_VIDEO = 50 * 1024 * 1024;
export const TIPOS_DE_VIDEO = ['video/mp4', 'video/webm'];
export const TAMANHO_TEXTO = 3000;
export const TAMANHO_COMENTARIO = 1000;

@Injectable({ providedIn: 'root' })
export class PublicacaoService {
  private readonly http = inject(HttpClient);

  pagina(escopo: EscopoFeed, ordem: OrdemFeed, cursor: string | null): Observable<PaginaFeed> {
    const { url, parametros } = enderecoDoFeed(escopo, ordem, cursor);
    return this.http.get<PaginaFeed>(url, { params: parametros });
  }

  /** Quantos posts chegaram ao feed depois de `desde`, a marca `topo` da primeira página. */
  novas(escopo: EscopoFeed, ordem: OrdemFeed, desde: string): Observable<NovasPublicacoes> {
    const { url, parametros } = enderecoDasNovas(escopo, ordem, desde);
    return this.http.get<NovasPublicacoes>(url, { params: parametros });
  }

  buscar(id: string): Observable<Publicacao> {
    return this.http.get<Publicacao>(`/api/publicacoes/${id}`);
  }

  publicar(nova: NovaPublicacao): Observable<Publicacao> {
    return this.http.post<Publicacao>('/api/publicacoes', formularioDaPublicacao(nova));
  }

  editar(id: string, conteudo: string): Observable<Publicacao> {
    return this.http.put<Publicacao>(`/api/publicacoes/${id}`, { conteudo });
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`/api/publicacoes/${id}`);
  }

  /** `ligar` falso desfaz. Os três são idempotentes no backend. */
  curtir(id: string, ligar: boolean): Observable<void> {
    return this.alternar(`/api/publicacoes/${id}/curtida`, ligar);
  }

  salvar(id: string, ligar: boolean): Observable<void> {
    return this.alternar(`/api/publicacoes/${id}/salva`, ligar);
  }

  republicar(id: string, ligar: boolean): Observable<void> {
    return this.alternar(`/api/publicacoes/${id}/republicacao`, ligar);
  }

  comentarios(id: string, ordem: OrdemComentarios): Observable<Comentario[]> {
    return this.http.get<Comentario[]>(`/api/publicacoes/${id}/comentarios`, { params: { ordem } });
  }

  /** `respondendoA` pode ser uma resposta: a nova pendura no mesmo comentário de cima. */
  comentar(id: string, conteudo: string, respondendoA: string | null): Observable<Comentario> {
    return this.http.post<Comentario>(`/api/publicacoes/${id}/comentarios`, { conteudo, respondendoA });
  }

  excluirComentario(comentarioId: string): Observable<void> {
    return this.http.delete<void>(`/api/publicacoes/comentarios/${comentarioId}`);
  }

  curtirComentario(comentarioId: string, ligar: boolean): Observable<void> {
    return this.alternar(`/api/publicacoes/comentarios/${comentarioId}/curtida`, ligar);
  }

  private alternar(url: string, ligar: boolean): Observable<void> {
    return ligar ? this.http.post<void>(url, null) : this.http.delete<void>(url);
  }
}

export function enderecoDoFeed(
  escopo: EscopoFeed,
  ordem: OrdemFeed,
  cursor: string | null,
): { url: string; parametros: HttpParams } {
  let parametros = new HttpParams();
  if (cursor) {
    parametros = parametros.set('cursor', cursor);
  }
  // O perfil e as salvas não têm chips: a ordem é sempre a da data.
  const comOrdem = (url: string) => ({
    url,
    parametros: ordem === 'recentes' ? parametros : parametros.set('ordem', ordem),
  });
  switch (escopo.tipo) {
    case 'inicio':
      if (escopo.bolhaId) {
        parametros = parametros.set('bolhaId', escopo.bolhaId);
      }
      return comOrdem('/api/publicacoes/feed');
    case 'bolha':
      return comOrdem(`/api/publicacoes/bolha/${escopo.bolhaId}`);
    case 'usuario':
      return { url: `/api/publicacoes/usuario/${escopo.usuarioId}`, parametros };
    case 'salvas':
      return { url: '/api/publicacoes/salvas', parametros };
  }
}

/**
 * O aviso de posts novos fica ao lado do feed, em `/novas`, com o mesmo filtro e o mesmo chip. Só o Início
 * e a bolha mandam a marca `topo`; nos outros feeds não há aviso.
 */
export function enderecoDasNovas(
  escopo: EscopoFeed,
  ordem: OrdemFeed,
  desde: string,
): { url: string; parametros: HttpParams } {
  const { url, parametros } = enderecoDoFeed(escopo, ordem, null);
  return { url: `${url}/novas`, parametros: parametros.set('desde', desde) };
}

/** "1 nova publicação", "3 novas publicações", "20+ novas publicações". */
export function rotuloDasNovas(novas: NovasPublicacoes): string {
  if (novas.mais) {
    return `${novas.total}+ novas publicações`;
  }
  return novas.total === 1 ? '1 nova publicação' : `${novas.total} novas publicações`;
}

/**
 * O multipart que a API espera: `dados` é um JSON (com o tipo marcado, senão o Spring não lê), e cada
 * arquivo vai na sua parte, `imagens` ou `video`.
 */
export function formularioDaPublicacao(nova: NovaPublicacao): FormData {
  const formulario = new FormData();
  const dados = { bolhaId: nova.bolhaId, conteudo: nova.conteudo, link: nova.link || null };
  formulario.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
  for (const imagem of nova.imagens) {
    formulario.append('imagens', imagem);
  }
  if (nova.video) {
    formulario.append('video', nova.video);
  }
  return formulario;
}

/** Mensagem de erro para mostrar antes de anexar, ou `null` se a imagem pode seguir. */
export function problemaNaImagemDoPost(arquivo: File): string | null {
  if (!TIPOS_DE_IMAGEM.includes(arquivo.type)) {
    return 'Escolha imagens JPG ou PNG.';
  }
  if (arquivo.size > TAMANHO_MAXIMO_IMAGEM) {
    return `${arquivo.name} passa de 5 MB.`;
  }
  return null;
}

/** Mensagem de erro para mostrar antes de anexar, ou `null` se o vídeo pode seguir. */
export function problemaNoVideo(arquivo: File): string | null {
  if (!TIPOS_DE_VIDEO.includes(arquivo.type)) {
    return 'Escolha um vídeo MP4 ou WebM.';
  }
  if (arquivo.size > TAMANHO_MAXIMO_VIDEO) {
    return 'O vídeo pode ter no máximo 50 MB.';
  }
  return null;
}

/** "https://www.ifba.edu.br/eventos" → "ifba.edu.br", para o cartão de um link sem prévia. */
export function dominio(url: string): string {
  try {
    return new URL(/^https?:\/\//i.test(url) ? url : `https://${url}`).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

/** "1 comentário", "3 comentários". */
export function comentariosPorExtenso(total: number): string {
  return `${total} ${total === 1 ? 'comentário' : 'comentários'}`;
}
