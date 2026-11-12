import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { ProblemaApi } from '../../auth/sessao';
import { BolhaService, MarcaDaBolha, corDaBolha } from '../../bolhas/bolha.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import {
  MAXIMO_IMAGENS,
  Publicacao,
  PublicacaoService,
  TAMANHO_TEXTO,
  dominio,
  problemaNaImagemDoPost,
  problemaNoVideo,
} from '../publicacao.service';

/** A bolha de destino, como o composer precisa dela. */
export type DestinoDoPost = MarcaDaBolha & { id: string };

interface Anexo {
  arquivo: File;
  previa: string;
}

/**
 * O `Composer` do MVP, agora publicando de verdade. A pílula "Postar em" mostra a bolha de destino: na
 * página de uma bolha ela é fixa; no Início vira um seletor com as bolhas da pessoa.
 *
 * Os botões Enquete, Evento e Emoji do MVP saíram (enquete não é requisito, e o teclado já tem emoji). No
 * lugar entraram Vídeo e Link. Um post leva até 4 imagens ou 1 vídeo, e um link. O cartão do link aqui é
 * simples; a prévia com título e imagem é montada pelo servidor ao publicar.
 */
@Component({
  selector: 'app-composer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Avatar, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './composer.html',
})
export class Composer {
  private readonly auth = inject(AuthService);
  private readonly bolhas = inject(BolhaService);
  private readonly servico = inject(PublicacaoService);
  private readonly toast = inject(ToastService);

  /** Na página de uma bolha, o destino não muda. */
  readonly bolhaFixa = input<DestinoDoPost | null>(null);
  /** No Início, a bolha do filtro vira o destino escolhido. */
  readonly bolhaPreferida = input<string | null>(null);

  readonly publicada = output<Publicacao>();

  private readonly campoTexto = viewChild<ElementRef<HTMLTextAreaElement>>('campoTexto');

  protected readonly usuario = this.auth.usuario;
  protected readonly minhas = computed(() => this.bolhas.minhas() ?? []);
  protected readonly escolhida = linkedSignal(() => this.bolhaPreferida() ?? this.minhas()[0]?.id ?? null);
  protected readonly destino = computed<DestinoDoPost | null>(
    () => this.bolhaFixa() ?? this.minhas().find((b) => b.id === this.escolhida()) ?? null,
  );
  protected readonly cor = computed(() => {
    const destino = this.destino();
    return destino ? corDaBolha(destino.tipo) : 'var(--accent)';
  });

  protected readonly texto = signal('');
  protected readonly imagens = signal<Anexo[]>([]);
  protected readonly video = signal<Anexo | null>(null);
  protected readonly mostrandoLink = signal(false);
  protected readonly link = signal('');
  protected readonly enviando = signal(false);

  protected readonly podePublicar = computed(
    () =>
      !!this.destino() &&
      !this.enviando() &&
      (!!this.texto().trim() || this.imagens().length > 0 || !!this.video() || !!this.link().trim()),
  );

  protected readonly tamanhoTexto = TAMANHO_TEXTO;
  protected readonly dominio = dominio;
  /** Recuo das sub-bolhas no seletor, como no menu lateral. Espaço comum some dentro de `<option>`. */
  protected readonly recuo = (nivel: number) => ' '.repeat(nivel * 3);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarPrevias());
  }

  /** Deixa o cursor no texto, para quem chegou pelo convite "Escreva o primeiro post". */
  focar(): void {
    this.campoTexto()?.nativeElement.focus();
  }

  protected escolherImagens(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivos = Array.from(campo.files ?? []);
    campo.value = '';
    if (this.video()) {
      this.toast.mostrar('Um post leva imagens ou um vídeo, não os dois.');
      return;
    }
    const vagas = MAXIMO_IMAGENS - this.imagens().length;
    if (arquivos.length > vagas) {
      this.toast.mostrar(`Um post leva até ${MAXIMO_IMAGENS} imagens.`);
    }
    const novos: Anexo[] = [];
    for (const arquivo of arquivos.slice(0, Math.max(0, vagas))) {
      const problema = problemaNaImagemDoPost(arquivo);
      if (problema) {
        this.toast.mostrar(problema);
        continue;
      }
      novos.push({ arquivo, previa: URL.createObjectURL(arquivo) });
    }
    this.imagens.update((atuais) => [...atuais, ...novos]);
  }

  protected escolherVideo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (!arquivo) {
      return;
    }
    if (this.imagens().length) {
      this.toast.mostrar('Um post leva imagens ou um vídeo, não os dois.');
      return;
    }
    const problema = problemaNoVideo(arquivo);
    if (problema) {
      this.toast.mostrar(problema);
      return;
    }
    this.removerVideo();
    this.video.set({ arquivo, previa: URL.createObjectURL(arquivo) });
  }

  protected removerImagem(anexo: Anexo): void {
    URL.revokeObjectURL(anexo.previa);
    this.imagens.update((atuais) => atuais.filter((a) => a !== anexo));
  }

  protected removerVideo(): void {
    const atual = this.video();
    if (atual) {
      URL.revokeObjectURL(atual.previa);
      this.video.set(null);
    }
  }

  protected alternarLink(): void {
    if (this.mostrandoLink()) {
      this.mostrandoLink.set(false);
      this.link.set('');
    } else {
      this.mostrandoLink.set(true);
    }
  }

  protected publicar(): void {
    const destino = this.destino();
    if (!destino || !this.podePublicar()) {
      return;
    }
    this.enviando.set(true);
    this.servico
      .publicar({
        bolhaId: destino.id,
        conteudo: this.texto().trim(),
        link: this.link().trim() || null,
        imagens: this.imagens().map((a) => a.arquivo),
        video: this.video()?.arquivo ?? null,
      })
      .subscribe({
        next: (publicacao) => {
          this.enviando.set(false);
          this.limpar();
          this.toast.mostrar(`Publicado em ${destino.nome}`);
          this.publicada.emit(publicacao);
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.toast.mostrar(mensagemDoErro(erro));
        },
      });
  }

  private limpar(): void {
    this.liberarPrevias();
    this.texto.set('');
    this.imagens.set([]);
    this.video.set(null);
    this.link.set('');
    this.mostrandoLink.set(false);
  }

  private liberarPrevias(): void {
    this.imagens().forEach((a) => URL.revokeObjectURL(a.previa));
    const video = this.video();
    if (video) {
      URL.revokeObjectURL(video.previa);
    }
  }
}

/** A primeira mensagem de campo da API, ou o `detail`, ou uma genérica. */
function mensagemDoErro(erro: unknown): string {
  if (erro instanceof HttpErrorResponse) {
    if (erro.status === 0) {
      return 'Sem conexão com o servidor. Tente de novo.';
    }
    const problema = erro.error as ProblemaApi | null;
    const primeiroCampo = problema?.erros ? Object.values(problema.erros)[0] : null;
    if (primeiroCampo || problema?.detail) {
      return primeiroCampo ?? problema!.detail!;
    }
  }
  return 'Não foi possível publicar. Tente de novo.';
}
