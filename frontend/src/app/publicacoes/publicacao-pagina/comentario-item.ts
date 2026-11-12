import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Avatar } from '../../shared/avatar';
import { dataCompleta, tempoRelativo } from '../../shared/tempo-relativo';
import { ToastService } from '../../shared/toast.service';
import { Comentario, PublicacaoService } from '../publicacao.service';
import { TextoPublicacao } from '../texto-publicacao';

/**
 * O `CommentItem` do MVP: o balão com nome e @username, e embaixo o tempo, "Curtir" e "Responder", que no
 * MVP só mostrava um aviso. Excluir pede confirmação ali mesmo. As respostas entram pelo conteúdo do
 * componente (`<ng-content>`), recuadas sob o comentário.
 */
@Component({
  selector: 'app-comentario-item',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, TextoPublicacao],
  styles: ':host { display: contents; }',
  template: `
    @let c = estado();
    <div class="comment" [class.comment-removido]="c.removido">
      @if (c.autor; as autor) {
        <a class="comment-avatar" [routerLink]="['/perfil', autor.username]" [attr.aria-label]="'Perfil de ' + autor.nomeCompleto">
          <app-avatar [nome]="autor.nomeCompleto" [fotoUrl]="autor.fotoUrl" tamanho="md" />
        </a>
      } @else {
        <div class="avatar md comment-avatar-vazio" aria-hidden="true"></div>
      }
      <div class="comment-main">
        <div class="comment-bubble">
          @if (c.autor; as autor) {
            <div class="comment-head">
              <a class="name" [routerLink]="['/perfil', autor.username]">{{ autor.nomeCompleto }}</a>
              <span class="handle">&#64;{{ autor.username }}</span>
            </div>
            <div class="comment-text"><app-texto-publicacao [texto]="c.conteudo ?? ''" /></div>
          } @else {
            <div class="comment-text">Comentário removido</div>
          }
        </div>
        @if (!c.removido) {
          <div class="comment-actions">
            <span [title]="quando()">{{ tempo() }}</span>
            <button type="button" [class.liked]="c.curtido" [attr.aria-pressed]="c.curtido" (click)="curtir()">
              {{ c.curtido ? 'Curtido' : 'Curtir' }}{{ c.curtidas ? ' · ' + c.curtidas : '' }}
            </button>
            @if (podeInteragir()) {
              <button type="button" (click)="responder.emit(c)">Responder</button>
            }
            @if (c.podeExcluir) {
              @if (confirmando()) {
                <span class="comment-confirmacao">
                  Excluir?
                  <button type="button" class="perigo" [disabled]="excluindo()" (click)="excluir()">Sim</button>
                  <button type="button" [disabled]="excluindo()" (click)="confirmando.set(false)">Não</button>
                </span>
              } @else {
                <button type="button" (click)="confirmando.set(true)">Excluir</button>
              }
            }
          </div>
        }
        <ng-content />
      </div>
    </div>
  `,
})
export class ComentarioItem {
  private readonly servico = inject(PublicacaoService);
  private readonly toast = inject(ToastService);

  readonly comentario = input.required<Comentario>();
  /** Membro da bolha do post: pode curtir e responder. */
  readonly podeInteragir = input(false);
  /** O nome da bolha, para o aviso de quem tenta curtir sem ser membro. */
  readonly nomeDaBolha = input('');

  readonly responder = output<Comentario>();
  readonly mudou = output<Comentario>();
  readonly excluido = output<string>();

  protected readonly estado = linkedSignal(() => this.comentario());
  protected readonly confirmando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly tempo = computed(() => tempoRelativo(this.estado().criadoEm));
  protected readonly quando = computed(() => dataCompleta(this.estado().criadoEm));

  protected curtir(): void {
    const antes = this.estado();
    if (!this.podeInteragir()) {
      this.toast.mostrar(`Entre em ${this.nomeDaBolha()} para curtir`);
      return;
    }
    const ligar = !antes.curtido;
    this.aplicar({ ...antes, curtido: ligar, curtidas: antes.curtidas + (ligar ? 1 : -1) });
    this.servico.curtirComentario(antes.id, ligar).subscribe({
      error: () => {
        this.aplicar(antes);
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
      },
    });
  }

  protected excluir(): void {
    this.excluindo.set(true);
    this.servico.excluirComentario(this.estado().id).subscribe({
      next: () => {
        this.toast.mostrar('Comentário excluído');
        this.excluido.emit(this.estado().id);
      },
      error: () => {
        this.excluindo.set(false);
        this.toast.mostrar('Não foi possível excluir. Tente de novo.');
      },
    });
  }

  private aplicar(comentario: Comentario): void {
    this.estado.set(comentario);
    this.mudou.emit(comentario);
  }
}
