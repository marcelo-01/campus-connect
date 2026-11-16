import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { BolhaResumo } from '../bolhas/bolha.service';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { TAMANHO_MAXIMO_IMAGEM, TIPOS_DE_IMAGEM } from '../shared/imagem';
import { ModalidadePreco } from './preco';

export type TipoAnuncio = 'SERVICO' | 'PRODUTO';
export type StatusAnuncio = 'PUBLICADO' | 'PAUSADO';

/** Uma categoria do marketplace. Um anúncio só usa categorias do próprio tipo. */
export interface Categoria {
  id: string;
  nome: string;
  slug: string;
  tipo: TipoAnuncio;
}

/** Um anúncio na vitrine ou no perfil (`AnuncioCard` no backend). */
export interface AnuncioCard {
  id: string;
  tipo: TipoAnuncio;
  titulo: string;
  categoria: Categoria;
  modalidadePreco: ModalidadePreco | null;
  /** Só em preço fixo e por hora. */
  preco: number | null;
  /** A primeira foto, ou `null` para o card mostrar o gradiente. */
  capaUrl: string | null;
  anunciante: PessoaPublica;
  /** Das mais amplas para as mais específicas; vazia se as bolhas do anúncio foram excluídas. */
  bolhas: BolhaResumo[];
  /** `null` enquanto ninguém avaliou. */
  media: number | null;
  totalAvaliacoes: number;
  favorito: boolean;
  /** Pausado só aparece para quem anunciou. */
  status: StatusAnuncio;
}

export interface Avaliacao {
  id: string;
  autor: PessoaPublica;
  nota: number;
  comentario: string | null;
  criadoEm: string;
  podeExcluir: boolean;
}

/** A página de um anúncio (`AnuncioDetalhe` no backend). */
export interface AnuncioDetalhe extends AnuncioCard {
  descricao: string | null;
  /** As fotos na ordem; a primeira é a capa. */
  imagens: string[];
  publicadoEm: string | null;
  minhaAvaliacao: Avaliacao | null;
  /** Quem anunciou edita, pausa e reativa. */
  podeEditar: boolean;
  /** Quem anunciou ou um administrador. */
  podeExcluir: boolean;
  /** Publicado e de outra pessoa. */
  podeAvaliar: boolean;
}

export interface PaginaAnuncios {
  itens: AnuncioCard[];
  /** Cursor da página seguinte; `null` na última. */
  proximo: string | null;
  /** Quantos anúncios passam no filtro. Só vem na primeira página da vitrine. */
  total: number | null;
}

/** Os botões do `.scope-toggle` da vitrine. */
export type EscopoVitrine = 'bolhas' | 'rede' | 'favoritos';
export type OrdemVitrine = 'recentes' | 'preco-asc' | 'preco-desc' | 'avaliacao';

export interface FiltroVitrine {
  escopo: EscopoVitrine;
  /** Só os anúncios direcionados a esta bolha. */
  bolhaId: string | null;
  tipo: TipoAnuncio | null;
  /** O slug da categoria. */
  categoria: string | null;
  busca: string;
  ordem: OrdemVitrine;
}

/** De onde vêm os anúncios de uma lista. */
export type FonteAnuncios =
  | { tipo: 'vitrine'; filtro: FiltroVitrine }
  /** Os anúncios de uma pessoa; ela vê também os pausados. */
  | { tipo: 'usuario'; usuarioId: string };

/** O que a pessoa preenche ao anunciar ou ao editar. As fotos vão à parte. */
export interface DadosAnuncio {
  tipo: TipoAnuncio;
  categoriaId: string;
  titulo: string;
  descricao: string;
  modalidadePreco: ModalidadePreco;
  preco: number | null;
  bolhaIds: string[];
}

/** Limites conferidos de novo pelo backend. */
export const MAXIMO_FOTOS = 6;
export const MAXIMO_BOLHAS = 5;
export const TAMANHO_TITULO = 120;
export const TAMANHO_DESCRICAO = 3000;
export const TAMANHO_COMENTARIO_AVALIACAO = 500;

@Injectable({ providedIn: 'root' })
export class AnuncioService {
  private readonly http = inject(HttpClient);

  private readonly categoriasCarregadas = signal<Categoria[] | null>(null);
  /** As categorias, que mudam só por migration. `null` até a primeira carga. */
  readonly categorias = this.categoriasCarregadas.asReadonly();
  private carregandoCategorias = false;

  /** Busca as categorias uma vez; quem precisa delas chama ao abrir. */
  carregarCategorias(): void {
    if (this.categoriasCarregadas() || this.carregandoCategorias) {
      return;
    }
    this.carregandoCategorias = true;
    this.http.get<Categoria[]>('/api/anuncios/categorias').subscribe({
      next: (categorias) => {
        this.categoriasCarregadas.set(categorias);
        this.carregandoCategorias = false;
      },
      error: () => {
        // Fica `null`: a próxima tela que pedir tenta de novo.
        this.carregandoCategorias = false;
      },
    });
  }

  pagina(fonte: FonteAnuncios, cursor: string | null): Observable<PaginaAnuncios> {
    const { url, parametros } = enderecoDosAnuncios(fonte, cursor);
    return this.http.get<PaginaAnuncios>(url, { params: parametros });
  }

  buscar(id: string): Observable<AnuncioDetalhe> {
    return this.http.get<AnuncioDetalhe>(`/api/anuncios/${id}`);
  }

  publicar(dados: DadosAnuncio, fotos: File[]): Observable<AnuncioDetalhe> {
    return this.http.post<AnuncioDetalhe>('/api/anuncios', formularioDoAnuncio(dados, fotos));
  }

  /** `mantidas` são as fotos atuais que ficam, na ordem; as novas entram depois delas. */
  editar(id: string, dados: DadosAnuncio, mantidas: string[], fotos: File[]): Observable<AnuncioDetalhe> {
    return this.http.put<AnuncioDetalhe>(`/api/anuncios/${id}`, formularioDoAnuncio(dados, fotos, mantidas));
  }

  /** `pausar` falso reativa. */
  pausar(id: string, pausar: boolean): Observable<void> {
    return this.alternar(`/api/anuncios/${id}/pausa`, pausar);
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`/api/anuncios/${id}`);
  }

  favoritar(id: string, ligar: boolean): Observable<void> {
    return this.alternar(`/api/anuncios/${id}/favorito`, ligar);
  }

  avaliacoes(id: string): Observable<Avaliacao[]> {
    return this.http.get<Avaliacao[]>(`/api/anuncios/${id}/avaliacoes`);
  }

  /** Cria a avaliação de quem está logado ou troca a que já existe. */
  avaliar(id: string, nota: number, comentario: string): Observable<Avaliacao> {
    return this.http.put<Avaliacao>(`/api/anuncios/${id}/avaliacao`, {
      nota,
      comentario: comentario.trim() || null,
    });
  }

  apagarAvaliacao(anuncioId: string, avaliacaoId: string): Observable<void> {
    return this.http.delete<void>(`/api/anuncios/${anuncioId}/avaliacoes/${avaliacaoId}`);
  }

  private alternar(url: string, ligar: boolean): Observable<void> {
    return ligar ? this.http.post<void>(url, null) : this.http.delete<void>(url);
  }
}

/** O endereço e os parâmetros de uma página. Os filtros no padrão não vão na URL. */
export function enderecoDosAnuncios(
  fonte: FonteAnuncios,
  cursor: string | null,
): { url: string; parametros: HttpParams } {
  let parametros = new HttpParams();
  if (cursor) {
    parametros = parametros.set('cursor', cursor);
  }
  if (fonte.tipo === 'usuario') {
    return { url: `/api/anuncios/usuario/${fonte.usuarioId}`, parametros };
  }
  const filtro = fonte.filtro;
  parametros = parametros.set('escopo', filtro.escopo);
  if (filtro.bolhaId) {
    parametros = parametros.set('bolhaId', filtro.bolhaId);
  }
  if (filtro.tipo) {
    parametros = parametros.set('tipo', filtro.tipo);
  }
  if (filtro.categoria) {
    parametros = parametros.set('categoria', filtro.categoria);
  }
  if (filtro.busca.trim()) {
    parametros = parametros.set('q', filtro.busca.trim());
  }
  if (filtro.ordem !== 'recentes') {
    parametros = parametros.set('ordem', filtro.ordem);
  }
  return { url: '/api/anuncios', parametros };
}

/**
 * O multipart que a API espera: `dados` é um JSON (com o tipo marcado, senão o Spring não lê), e cada foto
 * nova vai numa parte `imagens`. Na edição, `imagensMantidas` diz quais fotos atuais ficam.
 */
export function formularioDoAnuncio(dados: DadosAnuncio, fotos: File[], mantidas?: string[]): FormData {
  const formulario = new FormData();
  const json = mantidas ? { ...dados, imagensMantidas: mantidas } : dados;
  formulario.append('dados', new Blob([JSON.stringify(json)], { type: 'application/json' }));
  for (const foto of fotos) {
    formulario.append('imagens', foto);
  }
  return formulario;
}

/** Mensagem de erro para mostrar antes de anexar, ou `null` se a foto pode seguir. */
export function problemaNaFoto(arquivo: File): string | null {
  if (!TIPOS_DE_IMAGEM.includes(arquivo.type)) {
    return 'Escolha fotos JPG ou PNG.';
  }
  if (arquivo.size > TAMANHO_MAXIMO_IMAGEM) {
    return `${arquivo.name} passa de 5 MB.`;
  }
  return null;
}

export function rotuloDoTipoDeAnuncio(tipo: TipoAnuncio): string {
  return tipo === 'SERVICO' ? 'Serviço' : 'Produto';
}

/** "1 anúncio", "12 anúncios". */
export function anunciosPorExtenso(total: number): string {
  return total === 1 ? '1 anúncio' : `${total} anúncios`;
}

/** "1 avaliação", "38 avaliações". */
export function avaliacoesPorExtenso(total: number): string {
  return total === 1 ? '1 avaliação' : `${total} avaliações`;
}
