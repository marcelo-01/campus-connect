import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { comArquivo } from '../shared/imagem';

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

  totalDeConexoes(usuarioId: string): Observable<number> {
    return this.http
      .get<{ total: number }>(`/api/conexoes/usuario/${usuarioId}/total`)
      .pipe(map((resposta) => resposta.total));
  }
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
