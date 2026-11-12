import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { ProblemaApi } from '../../auth/sessao';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { PublicacaoCard } from '../publicacao-card/publicacao-card';
import {
  Comentario,
  OrdemComentarios,
  Publicacao,
  PublicacaoService,
  TAMANHO_COMENTARIO,
  comentariosPorExtenso,
} from '../publicacao.service';
import { ComentarioItem } from './comentario-item';

/** A quem a pessoa está respondendo; a resposta vai para o comentário de cima (`principalId`). */
interface Resposta {
  comentario: Comentario;
  principalId: string;
}

/**
 * O `CommentsScreen` do MVP: a barra "Publicação", o card do post, a ordem dos comentários, o campo de
 * comentar e a lista. Comentar e responder exigem ser membro da bolha do post; quem não é vê um convite
 * para entrar nela. As respostas têm um nível só, como no backend.
 */
@Component({
  selector: 'app-publicacao-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone, PublicacaoCard, ComentarioItem],
  styles: ':host { display: contents; }',
  templateUrl: './publicacao-pagina.html',
})
export class PublicacaoPagina {
  private readonly servico = inject(PublicacaoService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  protected readonly usuario = inject(AuthService).usuario;
  protected readonly publicacao = signal<Publicacao | null>(null);
  protected readonly naoEncontrada = signal(false);
  protected readonly comentarios = signal<Comentario[] | null>(null);
  protected readonly ordem = signal<OrdemComentarios>('relevantes');
  protected readonly texto = signal('');
  protected readonly respondendo = signal<Resposta | null>(null);
  protected readonly enviando = signal(false);

  protected readonly tamanhoComentario = TAMANHO_COMENTARIO;
  protected readonly comentariosPorExtenso = comentariosPorExtenso;

  private readonly campo = viewChild<ElementRef<HTMLTextAreaElement>>('campoComentario');
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(
        map((parametros) => parametros.get('id') ?? ''),
        tap(() => {
          this.publicacao.set(null);
          this.comentarios.set(null);
          this.naoEncontrada.set(false);
          this.respondendo.set(null);
          this.texto.set('');
        }),
        switchMap((id) =>
          this.servico.buscar(id).pipe(
            catchError((erro: unknown) => {
              if (erro instanceof HttpErrorResponse && (erro.status === 404 || erro.status === 400)) {
                this.naoEncontrada.set(true);
              } else {
                this.toast.mostrar('Não foi possível abrir a publicação.');
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((publicacao) => {
        this.publicacao.set(publicacao);
        if (publicacao) {
          this.carregarComentarios();
        }
      });
  }

  protected voltar(): void {
    if (history.length > 1) {
      this.location.back();
    } else {
      void this.router.navigate(['/']);
    }
  }

  protected trocarOrdem(ordem: OrdemComentarios): void {
    this.ordem.set(ordem);
    this.carregarComentarios();
  }

  protected focarComentario(): void {
    this.campo()?.nativeElement.focus();
  }

  protected responder(comentario: Comentario, principalId: string): void {
    this.respondendo.set({ comentario, principalId });
    const mencao = comentario.autor ? `@${comentario.autor.username} ` : '';
    if (!this.texto().startsWith(mencao)) {
      this.texto.set(mencao + this.texto());
    }
    this.focarComentario();
  }

  /** Enter envia, como no MVP; Shift+Enter quebra a linha. */
  protected aoTeclar(evento: KeyboardEvent): void {
    if (evento.key === 'Enter' && !evento.shiftKey) {
      evento.preventDefault();
      this.comentar();
    }
  }

  protected comentar(): void {
    const publicacao = this.publicacao();
    const texto = this.texto().trim();
    if (!publicacao || !texto || this.enviando()) {
      return;
    }
    const respondendo = this.respondendo();
    this.enviando.set(true);
    this.servico.comentar(publicacao.id, texto, respondendo?.comentario.id ?? null).subscribe({
      next: (novo) => {
        this.enviando.set(false);
        this.texto.set('');
        this.respondendo.set(null);
        this.comentarios.update((lista) => {
          const atual = lista ?? [];
          if (respondendo) {
            return atual.map((c) =>
              c.id === respondendo.principalId ? { ...c, respostas: [...c.respostas, novo] } : c,
            );
          }
          return this.ordem() === 'recentes' ? [novo, ...atual] : [...atual, novo];
        });
        this.contar(+1);
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        const problema = erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
        this.toast.mostrar(
          problema?.erros?.['conteudo'] ?? problema?.detail ?? 'Não foi possível comentar. Tente de novo.',
        );
      },
    });
  }

  /** Comentário que some de vez, ou que fica como "removido" porque tem respostas. */
  protected aoExcluir(id: string): void {
    this.comentarios.update((lista) =>
      (lista ?? []).flatMap((c) => {
        if (c.id === id) {
          return c.respostas.length
            ? [{ ...c, autor: null, conteudo: null, removido: true, podeExcluir: false }]
            : [];
        }
        const respostas = c.respostas.filter((r) => r.id !== id);
        if (respostas.length === c.respostas.length) {
          return [c];
        }
        // O comentário de cima já tinha sido removido e ficou só por causa desta resposta.
        return c.removido && !respostas.length ? [] : [{ ...c, respostas }];
      }),
    );
    if (this.respondendo()?.comentario.id === id || this.respondendo()?.principalId === id) {
      this.respondendo.set(null);
    }
    this.contar(-1);
  }

  /** Guarda o estado de um comentário (curtida), para ele não voltar atrás quando a lista mudar. */
  protected aoMudar(comentario: Comentario): void {
    this.comentarios.update((lista) =>
      (lista ?? []).map((c) =>
        c.id === comentario.id
          ? { ...comentario, respostas: c.respostas }
          : { ...c, respostas: c.respostas.map((r) => (r.id === comentario.id ? comentario : r)) },
      ),
    );
  }

  protected aoExcluirPublicacao(): void {
    void this.router.navigate(['/']);
  }

  private carregarComentarios(): void {
    const publicacao = this.publicacao();
    if (!publicacao) {
      return;
    }
    this.comentarios.set(null);
    this.servico
      .comentarios(publicacao.id, this.ordem())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => this.comentarios.set(lista),
        error: () => {
          this.comentarios.set([]);
          this.toast.mostrar('Não foi possível carregar os comentários.');
        },
      });
  }

  private contar(diferenca: number): void {
    this.publicacao.update((p) => (p ? { ...p, comentarios: Math.max(0, p.comentarios + diferenca) } : p));
  }
}
