import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { corDaBolha } from '../../bolhas/bolha.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { dataCompleta, tempoRelativo } from '../../shared/tempo-relativo';
import { ToastService } from '../../shared/toast.service';
import {
  PessoaPublica,
  Publicacao,
  PublicacaoService,
  TAMANHO_TEXTO,
  dominio,
} from '../publicacao.service';
import { TextoPublicacao } from '../texto-publicacao';

/**
 * O `Post` do MVP com dados reais. Além do que o MVP tinha (cabeçalho, texto, imagem, curtir, comentar,
 * republicar, compartilhar e salvar), mostra até 4 imagens, vídeo, o cartão do link e o menu ⋯ com editar e
 * excluir, que no MVP não fazia nada. Editar e confirmar a exclusão acontecem no próprio card: o MVP não tem
 * modal.
 *
 * As ações mudam o card na hora e desfazem se a API recusar. Cada mudança sai em `mudou`, para a lista que
 * contém o card ficar em dia.
 */
@Component({
  selector: 'app-publicacao-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone, TextoPublicacao],
  styles: ':host { display: contents; }',
  templateUrl: './publicacao-card.html',
  host: {
    '(document:click)': 'fecharMenuSeForFora($event)',
    '(document:keydown.escape)': 'menuAberto.set(false)',
  },
})
export class PublicacaoCard {
  private readonly servico = inject(PublicacaoService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  readonly publicacao = input.required<Publicacao>();
  /** Quem republicou, no feed de um perfil. */
  readonly republicadaPor = input<PessoaPublica | null>(null);
  /** No feed, clicar no texto, na mídia ou nos comentários abre a publicação. Na página dela, não. */
  readonly navegavel = input(true);
  /** O primeiro card da tela: as imagens dele carregam já, sem esperar a rolagem (`loading="eager"`). */
  readonly prioritaria = input(false);

  readonly mudou = output<Publicacao>();
  readonly excluida = output<string>();
  /** Na página da publicação, o botão de comentários leva ao campo de comentar. */
  readonly querComentar = output<void>();

  protected readonly estado = linkedSignal(() => this.publicacao());
  protected readonly menuAberto = signal(false);
  protected readonly editando = signal(false);
  protected readonly textoEditado = signal('');
  protected readonly salvandoEdicao = signal(false);
  protected readonly confirmandoExclusao = signal(false);
  protected readonly excluindo = signal(false);

  protected readonly proprio = computed(() => this.estado().autor.id === this.auth.usuario()?.id);
  protected readonly imagens = computed(() => this.estado().midias.filter((m) => m.tipo === 'IMAGEM'));
  protected readonly video = computed(() => this.estado().midias.find((m) => m.tipo === 'VIDEO') ?? null);
  protected readonly cor = computed(() => corDaBolha(this.estado().bolha.tipo));
  protected readonly tempo = computed(() => tempoRelativo(this.estado().criadoEm));
  protected readonly quando = computed(() => dataCompleta(this.estado().criadoEm));
  protected readonly tituloRepublicar = computed(() => {
    const p = this.estado();
    if (this.proprio()) {
      return 'Não dá para republicar o próprio post';
    }
    return p.republicada ? 'Desfazer republicação' : 'Republicar no seu perfil';
  });

  protected readonly tamanhoTexto = TAMANHO_TEXTO;
  protected readonly dominio = dominio;

  protected abrir(): void {
    if (this.navegavel()) {
      void this.router.navigate(['/publicacoes', this.estado().id]);
    }
  }

  protected abrirComentarios(): void {
    if (this.navegavel()) {
      this.abrir();
    } else {
      this.querComentar.emit();
    }
  }

  // ---------------------------------------------------------------- interações

  protected curtir(): void {
    const antes = this.estado();
    if (!antes.podeInteragir) {
      this.toast.mostrar(`Entre em ${antes.bolha.nome} para curtir`);
      return;
    }
    const ligar = !antes.curtida;
    this.aplicar({ curtida: ligar, curtidas: antes.curtidas + (ligar ? 1 : -1) });
    this.toast.mostrar(ligar ? 'Você curtiu o post' : 'Curtida removida');
    this.servico.curtir(antes.id, ligar).subscribe({
      error: () => this.desfazer({ curtida: antes.curtida, curtidas: antes.curtidas }),
    });
  }

  protected republicar(): void {
    const antes = this.estado();
    if (this.proprio()) {
      return;
    }
    if (!antes.podeInteragir) {
      this.toast.mostrar(`Entre em ${antes.bolha.nome} para republicar`);
      return;
    }
    const ligar = !antes.republicada;
    this.aplicar({ republicada: ligar, republicacoes: antes.republicacoes + (ligar ? 1 : -1) });
    this.toast.mostrar(ligar ? 'Republicado no seu perfil' : 'Republicação desfeita');
    this.servico.republicar(antes.id, ligar).subscribe({
      error: () =>
        this.desfazer({ republicada: antes.republicada, republicacoes: antes.republicacoes }),
    });
  }

  protected salvar(): void {
    const antes = this.estado();
    const ligar = !antes.salva;
    this.aplicar({ salva: ligar });
    this.toast.mostrar(ligar ? 'Salvo' : 'Removido dos salvos');
    this.servico.salvar(antes.id, ligar).subscribe({
      error: () => this.desfazer({ salva: antes.salva }),
    });
  }

  protected async compartilhar(): Promise<void> {
    const endereco = `${location.origin}/publicacoes/${this.estado().id}`;
    try {
      await navigator.clipboard.writeText(endereco);
      this.toast.mostrar('Link copiado');
    } catch {
      this.toast.mostrar(endereco);
    }
  }

  // ---------------------------------------------------------------- menu ⋯

  protected alternarMenu(evento: Event): void {
    evento.stopPropagation();
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenuSeForFora(evento: Event): void {
    if (this.menuAberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.menuAberto.set(false);
    }
  }

  protected comecarEdicao(): void {
    this.menuAberto.set(false);
    this.confirmandoExclusao.set(false);
    this.textoEditado.set(this.estado().conteudo);
    this.editando.set(true);
  }

  protected salvarEdicao(): void {
    const texto = this.textoEditado().trim();
    if (!texto && !this.estado().midias.length && !this.estado().link) {
      this.toast.mostrar('Escreva algo ou exclua a publicação.');
      return;
    }
    this.salvandoEdicao.set(true);
    this.servico.editar(this.estado().id, texto).subscribe({
      next: (editada) => {
        this.estado.set(editada);
        this.mudou.emit(editada);
        this.editando.set(false);
        this.salvandoEdicao.set(false);
        this.toast.mostrar('Publicação editada');
      },
      error: () => {
        this.salvandoEdicao.set(false);
        this.toast.mostrar('Não foi possível salvar. Tente de novo.');
      },
    });
  }

  protected pedirExclusao(): void {
    this.menuAberto.set(false);
    this.editando.set(false);
    this.confirmandoExclusao.set(true);
  }

  protected excluir(): void {
    this.excluindo.set(true);
    this.servico.excluir(this.estado().id).subscribe({
      next: () => {
        this.toast.mostrar('Publicação excluída');
        this.excluida.emit(this.estado().id);
      },
      error: () => {
        this.excluindo.set(false);
        this.toast.mostrar('Não foi possível excluir. Tente de novo.');
      },
    });
  }

  // ---------------------------------------------------------------- apoio

  private aplicar(mudanca: Partial<Publicacao>): void {
    this.estado.update((atual) => ({ ...atual, ...mudanca }));
    this.mudou.emit(this.estado());
  }

  private desfazer(mudanca: Partial<Publicacao>): void {
    this.aplicar(mudanca);
    this.toast.mostrar('Não foi possível concluir. Tente de novo.');
  }
}
