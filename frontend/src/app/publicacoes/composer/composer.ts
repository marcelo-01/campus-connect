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
import { BolhaResumo, BolhaService, corDaBolha } from '../../bolhas/bolha.service';
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
  resumoDoDestino,
} from '../publicacao.service';

interface Anexo {
  arquivo: File;
  previa: string;
}

/**
 * O `Composer` do MVP, agora publicando de verdade. A pílula "Postar em" abre a lista das bolhas da pessoa,
 * cada uma com uma caixinha, e o post aparece só nas bolhas marcadas: não desce para as de dentro. Vem
 * marcada a bolha da página, ou a do filtro no Início; sem nenhuma das duas, a primeira da lista.
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
  host: {
    '(document:click)': 'fecharEscolhaSeForFora($event)',
    '(document:keydown.escape)': 'escolhendo.set(false)',
  },
})
export class Composer {
  private readonly auth = inject(AuthService);
  private readonly bolhas = inject(BolhaService);
  private readonly servico = inject(PublicacaoService);
  private readonly toast = inject(ToastService);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  /** A bolha que já vem marcada: a da página da bolha, ou a do filtro no Início. */
  readonly bolhaPreferida = input<string | null>(null);

  readonly publicada = output<Publicacao>();

  private readonly campoTexto = viewChild<ElementRef<HTMLTextAreaElement>>('campoTexto');

  protected readonly usuario = this.auth.usuario;
  protected readonly minhas = computed(() => this.bolhas.minhas() ?? []);
  /**
   * Os ids das bolhas marcadas. Trocar a bolha preferida (o filtro do Início, por exemplo) recomeça a
   * escolha. Recarregar a lista, como ao entrar numa bolha, só desmarca as que saíram dela.
   */
  protected readonly marcadas = linkedSignal<
    { preferida: string | null; minhas: BolhaResumo[] },
    ReadonlySet<string>
  >({
    source: () => ({ preferida: this.bolhaPreferida(), minhas: this.minhas() }),
    computation: ({ preferida, minhas }, anterior) => {
      const existentes = new Set(minhas.map((bolha) => bolha.id));
      if (anterior?.source.preferida === preferida) {
        const mantidas = [...anterior.value].filter((id) => existentes.has(id));
        if (mantidas.length) {
          return new Set(mantidas);
        }
      }
      const inicial = preferida && existentes.has(preferida) ? preferida : minhas[0]?.id;
      return new Set(inicial ? [inicial] : []);
    },
  });
  /**
   * As bolhas marcadas, na ordem da lista: das mais amplas para as de dentro. A pílula mostra o nome da
   * primeira, que encolhe com reticências, e o "e mais N" fora dele, para as outras nunca sumirem.
   */
  protected readonly destino = computed(() => this.minhas().filter((bolha) => this.marcadas().has(bolha.id)));
  protected readonly resumo = computed(() => resumoDoDestino(this.destino().map((bolha) => bolha.nome)));
  protected readonly cor = computed(() => {
    const primeira = this.destino()[0];
    return primeira ? corDaBolha(primeira.tipo) : 'var(--accent)';
  });
  protected readonly escolhendo = signal(false);

  protected readonly texto = signal('');
  protected readonly imagens = signal<Anexo[]>([]);
  protected readonly video = signal<Anexo | null>(null);
  protected readonly mostrandoLink = signal(false);
  protected readonly link = signal('');
  protected readonly enviando = signal(false);

  protected readonly podePublicar = computed(
    () =>
      this.destino().length > 0 &&
      !this.enviando() &&
      (!!this.texto().trim() || this.imagens().length > 0 || !!this.video() || !!this.link().trim()),
  );

  protected readonly tamanhoTexto = TAMANHO_TEXTO;
  protected readonly dominio = dominio;
  protected readonly corDaBolha = corDaBolha;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarPrevias());
  }

  /** Deixa o cursor no texto, para quem chegou pelo convite "Escreva o primeiro post". */
  focar(): void {
    this.campoTexto()?.nativeElement.focus();
  }

  // ---------------------------------------------------------------- bolhas de destino

  protected alternarEscolha(evento: Event): void {
    evento.stopPropagation();
    this.escolhendo.update((aberta) => !aberta);
  }

  protected fecharEscolhaSeForFora(evento: Event): void {
    if (this.escolhendo() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.escolhendo.set(false);
    }
  }

  protected alternarBolha(id: string): void {
    this.marcadas.update((atuais) => {
      const novas = new Set(atuais);
      if (!novas.delete(id)) {
        novas.add(id);
      }
      return novas;
    });
  }

  // ---------------------------------------------------------------- anexos

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

  // ---------------------------------------------------------------- publicar

  protected publicar(): void {
    if (!this.podePublicar()) {
      return;
    }
    const resumo = this.resumo();
    this.escolhendo.set(false);
    this.enviando.set(true);
    this.servico
      .publicar({
        bolhaIds: this.destino().map((bolha) => bolha.id),
        conteudo: this.texto().trim(),
        link: this.link().trim() || null,
        imagens: this.imagens().map((a) => a.arquivo),
        video: this.video()?.arquivo ?? null,
      })
      .subscribe({
        next: (publicacao) => {
          this.enviando.set(false);
          this.limpar();
          this.toast.mostrar(`Publicado em ${resumo}`);
          this.publicada.emit(publicacao);
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.toast.mostrar(mensagemDoErro(erro));
        },
      });
  }

  /** O texto e os anexos saem; as bolhas marcadas ficam para o próximo post. */
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
