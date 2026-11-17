import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject, catchError, map, merge, of, switchMap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { PessoaPublica } from '../publicacoes/publicacao.service';
import { AoVivoService } from '../shared/aovivo/aovivo.service';

/** O mesmo limite do servidor, em caracteres (um emoji conta como um). */
export const TAMANHO_MAXIMO_MENSAGEM = 2000;

/** Os eventos ao vivo das conversas. */
export const EVENTO_MENSAGEM = 'mensagem';
export const EVENTO_LEITURA = 'leitura';
export const EVENTO_MENSAGEM_APAGADA = 'mensagem-apagada';

/** Uma mensagem. É também o dado do evento ao vivo `mensagem`, que vai igual para as duas pessoas. */
export interface Mensagem {
  id: string;
  conversaId: string;
  remetenteId: string;
  /** `null` se a mensagem foi apagada. */
  conteudo: string | null;
  enviadoEm: string;
  apagada: boolean;
}

/** A conversa aberta. */
export interface Conversa {
  id: string;
  pessoa: PessoaPublica;
  /** `false` se há bloqueio entre as duas pessoas, em qualquer sentido. */
  podeEnviar: boolean;
  /** Se quem está logado bloqueou a outra pessoa. */
  bloqueei: boolean;
  /** Até quando a outra pessoa leu, para o "Visto". */
  outroLeuEm: string | null;
}

/** Uma conversa na lista. */
export interface ResumoConversa {
  id: string;
  pessoa: PessoaPublica;
  ultima: {
    mensagemId: string;
    /** O começo do texto, numa linha só; `null` se a mensagem foi apagada. */
    texto: string | null;
    minha: boolean;
    enviadaEm: string;
    apagada: boolean;
  };
  naoLida: boolean;
}

export interface Pagina<T> {
  itens: T[];
  /** Onde a próxima página começa; `null` na última. */
  proximo: string | null;
}

export interface EventoLeitura {
  conversaId: string;
  leitorId: string;
  ate: string;
}

export interface EventoMensagemApagada {
  conversaId: string;
  mensagemId: string;
}

export interface FiltroConversas {
  cursor?: string | null;
  busca?: string;
  soNaoLidas?: boolean;
}

interface Contagem {
  usuarioId: string;
  total: number;
}

/**
 * Mensagens privadas (RF05). Guarda o número de conversas não lidas, que aparece no item "Mensagens" do menu, e
 * o mantém em dia com os eventos ao vivo.
 */
@Injectable({ providedIn: 'root' })
export class MensagemService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly aoVivo = inject(AoVivoService);

  private readonly contagem = signal<Contagem | null>(null);
  private readonly recargas = new Subject<string>();

  /** Quantas conversas têm mensagem não lida. Zero enquanto não carregou ou se a contagem é de outra conta. */
  readonly naoLidas = computed(() => {
    const contagem = this.contagem();
    return contagem && contagem.usuarioId === this.auth.usuario()?.id ? contagem.total : 0;
  });

  constructor() {
    // A recarga mais nova vence: uma resposta atrasada não sobrescreve uma mais recente.
    this.recargas
      .pipe(
        switchMap((usuarioId) =>
          this.http.get<{ total: number }>('/api/conversas/nao-lidas').pipe(
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

    merge(
      this.aoVivo.sincronizar,
      this.aoVivo.eventos(EVENTO_MENSAGEM),
      this.aoVivo.eventos(EVENTO_LEITURA),
      this.aoVivo.eventos(EVENTO_MENSAGEM_APAGADA),
    ).subscribe(() => this.recarregarNaoLidas());
  }

  recarregarNaoLidas(): void {
    const usuarioId = this.auth.usuario()?.id;
    if (usuarioId) {
      this.recargas.next(usuarioId);
    }
  }

  /** Abre a conversa com a pessoa: a que já existe ou uma nova, vazia. */
  abrirCom(usuarioId: string): Observable<Conversa> {
    return this.http.post<Conversa>(`/api/conversas/com/${usuarioId}`, null);
  }

  listar(filtro: FiltroConversas = {}): Observable<Pagina<ResumoConversa>> {
    let params = new HttpParams();
    if (filtro.cursor) {
      params = params.set('cursor', filtro.cursor);
    }
    if (filtro.busca?.trim()) {
      params = params.set('q', filtro.busca.trim());
    }
    if (filtro.soNaoLidas) {
      params = params.set('filtro', 'nao-lidas');
    }
    return this.http.get<Pagina<ResumoConversa>>('/api/conversas', { params });
  }

  buscar(conversaId: string): Observable<Conversa> {
    return this.http.get<Conversa>(`/api/conversas/${conversaId}`);
  }

  /** Uma página da conversa, da mensagem mais nova para a mais antiga. */
  mensagens(conversaId: string, cursor: string | null = null): Observable<Pagina<Mensagem>> {
    const params = cursor ? new HttpParams().set('cursor', cursor) : undefined;
    return this.http.get<Pagina<Mensagem>>(`/api/conversas/${conversaId}/mensagens`, { params });
  }

  enviar(conversaId: string, conteudo: string): Observable<Mensagem> {
    return this.http.post<Mensagem>(`/api/conversas/${conversaId}/mensagens`, { conteudo });
  }

  lerAte(conversaId: string, mensagemId: string): Observable<void> {
    return this.http.post<void>(`/api/conversas/${conversaId}/leitura`, { ate: mensagemId });
  }

  apagar(conversaId: string, mensagemId: string): Observable<void> {
    return this.http.delete<void>(`/api/conversas/${conversaId}/mensagens/${mensagemId}`);
  }
}

// ------------------------------------------------------------ funções

/**
 * Junta mensagens novas às que já estão na tela, sem repetir: a mensagem enviada chega pela resposta do envio e
 * pela conexão ao vivo, muitas vezes primeiro pela conexão. A versão nova vence (uma mensagem que foi apagada
 * chega de novo como apagada). A ordem é a do servidor: data e id.
 */
export function mesclar(atuais: readonly Mensagem[], novas: readonly Mensagem[]): Mensagem[] {
  const porId = new Map(atuais.map((mensagem) => [mensagem.id, mensagem]));
  novas.forEach((mensagem) => porId.set(mensagem.id, mensagem));
  return [...porId.values()].sort(
    (a, b) => compararInstantes(a.enviadoEm, b.enviadoEm) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
  );
}

/**
 * Compara dois instantes ISO em UTC até o microssegundo. A comparação de texto não serve direto, porque o servidor
 * corta os zeros do fim da fração ("…:36.2Z" e "…:36.215Z").
 */
export function compararInstantes(a: string, b: string): number {
  const x = normalizar(a);
  const y = normalizar(b);
  return x < y ? -1 : x > y ? 1 : 0;
}

function normalizar(instante: string): string {
  const [semZona] = instante.split('Z');
  const [segundos, fracao = ''] = semZona.split('.');
  return `${segundos}.${fracao.padEnd(9, '0')}`;
}

/** Uma mensagem apagada, como ela fica depois do evento `mensagem-apagada`. */
export function comoApagada(mensagem: Mensagem): Mensagem {
  return { ...mensagem, conteudo: null, apagada: true };
}

/** As mensagens agrupadas por dia, na ordem, com o rótulo do separador: "Hoje", "Ontem" ou a data. */
export function separarPorDia<T extends { enviadoEm: string }>(
  mensagens: readonly T[],
  agora: Date = new Date(),
): { dia: string; mensagens: T[] }[] {
  const grupos: { dia: string; mensagens: T[] }[] = [];
  for (const mensagem of mensagens) {
    const dia = rotuloDoDia(mensagem.enviadoEm, agora);
    const ultimo = grupos.at(-1);
    if (ultimo && ultimo.dia === dia) {
      ultimo.mensagens.push(mensagem);
    } else {
      grupos.push({ dia, mensagens: [mensagem] });
    }
  }
  return grupos;
}

/** "Hoje", "Ontem", "12 de março" ou, em outro ano, "12 de março de 2025". */
export function rotuloDoDia(dataIso: string, agora: Date = new Date()): string {
  const data = new Date(dataIso);
  const inicio = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const dias = Math.round((inicio(agora) - inicio(data)) / 86_400_000);
  if (dias === 0) {
    return 'Hoje';
  }
  if (dias === 1) {
    return 'Ontem';
  }
  const opcoes: Intl.DateTimeFormatOptions =
    data.getFullYear() === agora.getFullYear()
      ? { day: 'numeric', month: 'long' }
      : { day: 'numeric', month: 'long', year: 'numeric' };
  return data.toLocaleDateString('pt-BR', opcoes);
}

/** A prévia da lista: "Você: …" para a mensagem de quem está logado e "Mensagem apagada" para a apagada. */
export function previa(resumo: ResumoConversa): string {
  if (resumo.ultima.apagada || resumo.ultima.texto === null) {
    return 'Mensagem apagada';
  }
  return resumo.ultima.minha ? `Você: ${resumo.ultima.texto}` : resumo.ultima.texto;
}

/** O começo do texto numa linha só, como o servidor monta a prévia da lista (120 caracteres). */
export function cortarPrevia(texto: string): string {
  const linha = texto.replace(/\s+/g, ' ').trim();
  const caracteres = [...linha];
  return caracteres.length <= 120 ? linha : `${caracteres.slice(0, 120).join('').trimEnd()}…`;
}

/** Quantos caracteres o texto tem, contando um emoji como um, como o servidor. */
export function tamanhoDaMensagem(texto: string): number {
  return [...texto.trim()].length;
}
