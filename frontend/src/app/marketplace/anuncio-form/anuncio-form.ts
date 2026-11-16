import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map, startWith } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { BolhaService } from '../../bolhas/bolha.service';
import { aplicarErroDaApi, mensagemDeErro } from '../../shared/formulario';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { AnuncioCartao } from '../anuncio-cartao/anuncio-cartao';
import {
  AnuncioCard,
  AnuncioDetalhe,
  AnuncioService,
  DadosAnuncio,
  MAXIMO_BOLHAS,
  MAXIMO_FOTOS,
  TAMANHO_DESCRICAO,
  TAMANHO_TITULO,
  TipoAnuncio,
  problemaNaFoto,
} from '../anuncio.service';
import { ModalidadePreco, pedePreco } from '../preco';

type Campo = 'categoriaId' | 'titulo' | 'descricao' | 'modalidadePreco' | 'preco' | 'bolhaIds';

const MENSAGENS: Record<Campo, Record<string, string>> = {
  categoriaId: { required: 'Escolha uma categoria.' },
  titulo: {
    required: 'Dê um título ao anúncio.',
    minlength: 'O título precisa de pelo menos 3 caracteres.',
    maxlength: `Use no máximo ${TAMANHO_TITULO} caracteres.`,
  },
  descricao: {
    required: 'Descreva o que você oferece.',
    maxlength: `A descrição pode ter até ${TAMANHO_DESCRICAO} caracteres.`,
  },
  modalidadePreco: { required: 'Escolha como você cobra.' },
  preco: {
    required: 'Informe o preço.',
    min: 'O preço precisa ser maior que zero.',
    max: 'O preço pode ser de até R$ 1.000.000,00.',
  },
  bolhaIds: {
    nenhuma: 'Escolha pelo menos uma bolha.',
    demais: `Escolha até ${MAXIMO_BOLHAS} bolhas.`,
  },
};

const MODALIDADES: { valor: ModalidadePreco; rotulo: string }[] = [
  { valor: 'FIXO', rotulo: 'Preço fixo' },
  { valor: 'POR_HORA', rotulo: 'Por hora' },
  { valor: 'A_COMBINAR', rotulo: 'A combinar' },
  { valor: 'GRATUITO', rotulo: 'Grátis' },
];

/** Uma foto que o anúncio já tem (pela URL) ou uma escolhida agora, com a prévia local. */
type Foto = { url: string } | { arquivo: File; previa: string };

function bolhasEscolhidas(controle: AbstractControl<string[]>): ValidationErrors | null {
  const total = controle.value.length;
  return total === 0 ? { nenhuma: true } : total > MAXIMO_BOLHAS ? { demais: true } : null;
}

/**
 * O `CreateServiceScreen` do MVP, para anunciar e editar: o formulário à esquerda e, à direita, o card do
 * anúncio como vai aparecer na vitrine.
 *
 * Do MVP saíram o seletor de gradiente da capa (a capa agora é a primeira foto; sem foto, o card escolhe um
 * gradiente), o prazo de entrega e o impulsionamento, que vem numa etapa própria. Entraram o tipo (serviço ou
 * produto), as fotos e as bolhas de destino: de 1 a 5, entre as bolhas em que a pessoa está.
 */
@Component({
  selector: 'app-anuncio-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Icone, AnuncioCartao],
  styles: ':host { display: contents; }',
  templateUrl: './anuncio-form.html',
})
export class AnuncioForm {
  private readonly servico = inject(AnuncioService);
  private readonly bolhas = inject(BolhaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Na edição, o anúncio como está no banco; ao anunciar, `null`. */
  protected readonly original = signal<AnuncioDetalhe | null>(null);
  protected readonly edicao = !!inject(ActivatedRoute).snapshot.paramMap.get('id');
  protected readonly carregando = signal(this.edicao);
  protected readonly salvando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  protected readonly fotos = signal<Foto[]>([]);

  protected readonly confirmandoExclusao = signal(false);
  protected readonly excluindo = signal(false);

  protected readonly modalidades = MODALIDADES;
  protected readonly maximoFotos = MAXIMO_FOTOS;
  protected readonly maximoBolhas = MAXIMO_BOLHAS;
  protected readonly tamanhoTitulo = TAMANHO_TITULO;
  protected readonly tamanhoDescricao = TAMANHO_DESCRICAO;
  /** `null` enquanto o menu lateral ainda carrega as bolhas. */
  protected readonly minhas = this.bolhas.minhas;
  protected readonly categorias = this.servico.categorias;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    tipo: ['SERVICO' as TipoAnuncio],
    titulo: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(TAMANHO_TITULO)]],
    categoriaId: ['', Validators.required],
    descricao: ['', [Validators.required, Validators.maxLength(TAMANHO_DESCRICAO)]],
    modalidadePreco: ['FIXO' as ModalidadePreco, Validators.required],
    preco: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(1_000_000)]],
    // Um array como valor confundiria o FormBuilder, que leria [valor, validadores].
    bolhaIds: new FormControl<string[]>([], { nonNullable: true, validators: bolhasEscolhidas }),
  });

  /** O formulário inteiro como signal, para a pré-visualização e as listas que dependem do tipo. */
  protected readonly valores = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.form.getRawValue()),
    ),
    { requireSync: true },
  );

  protected readonly tipo = computed(() => this.valores().tipo);
  protected readonly categoriasDoTipo = computed(() =>
    (this.categorias() ?? []).filter((c) => c.tipo === this.tipo()),
  );
  protected readonly mostraPreco = computed(() => pedePreco(this.valores().modalidadePreco));
  protected readonly escolhidas = computed(() => new Set(this.valores().bolhaIds));

  /** O card da vitrine com o que está no formulário. */
  protected readonly previa = computed<AnuncioCard>(() => {
    const valores = this.valores();
    const usuario = this.auth.usuario();
    const categoria = this.categorias()?.find((c) => c.id === valores.categoriaId);
    const primeira = this.fotos()[0];
    const escolhidas = this.escolhidas();
    return {
      id: this.original()?.id ?? 'novo',
      tipo: valores.tipo,
      titulo:
        valores.titulo.trim() ||
        (valores.tipo === 'SERVICO' ? 'Título do seu serviço' : 'Título do seu produto'),
      categoria: categoria ?? { id: '', nome: '', slug: '', tipo: valores.tipo },
      modalidadePreco: valores.modalidadePreco,
      preco: pedePreco(valores.modalidadePreco) ? Number(valores.preco) || 0 : null,
      capaUrl: primeira ? ('url' in primeira ? primeira.url : primeira.previa) : null,
      anunciante: {
        id: usuario?.id ?? '',
        username: usuario?.username ?? '',
        nomeCompleto: usuario?.nomeCompleto ?? '',
        fotoUrl: usuario?.fotoUrl ?? null,
      },
      bolhas: (this.minhas() ?? []).filter((b) => escolhidas.has(b.id)),
      media: null,
      totalAvaliacoes: 0,
      favorito: false,
      status: this.original()?.status ?? 'PUBLICADO',
    };
  });

  constructor() {
    this.servico.carregarCategorias();
    const id = inject(ActivatedRoute).snapshot.paramMap.get('id');
    if (id) {
      this.servico.buscar(id).subscribe({
        next: (anuncio) => {
          if (!anuncio.podeEditar) {
            void this.router.navigate(['/marketplace', anuncio.id]);
            return;
          }
          this.preencher(anuncio);
        },
        error: (erro: unknown) => {
          this.carregando.set(false);
          this.erroGeral.set(
            erro instanceof HttpErrorResponse && erro.status === 404
              ? 'Esse anúncio não existe mais.'
              : 'Não foi possível carregar o anúncio. Recarregue a página.',
          );
        },
      });
    }
    // O preço só vale em preço fixo e por hora.
    this.form.controls.modalidadePreco.valueChanges
      .pipe(startWith(this.form.controls.modalidadePreco.value), takeUntilDestroyed())
      .subscribe((modalidade) => {
        const preco = this.form.controls.preco;
        if (pedePreco(modalidade)) {
          preco.enable({ emitEvent: false });
        } else {
          preco.setValue(null, { emitEvent: false });
          preco.disable({ emitEvent: false });
        }
      });
    // Trocar o tipo tira a categoria, que pode ser do outro tipo.
    this.form.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => {
      const categoria = this.categorias()?.find((c) => c.id === this.form.controls.categoriaId.value);
      if (categoria && categoria.tipo !== tipo) {
        this.form.controls.categoriaId.setValue('');
      }
    });
    inject(DestroyRef).onDestroy(() => this.fotos().forEach(liberar));
  }

  // ----------------------------------------------------------------- bolhas

  protected alternarBolha(id: string): void {
    const controle = this.form.controls.bolhaIds;
    const atuais = controle.value;
    controle.setValue(atuais.includes(id) ? atuais.filter((b) => b !== id) : [...atuais, id]);
    controle.markAsTouched();
  }

  // ------------------------------------------------------------------ fotos

  protected escolherFotos(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivos = Array.from(campo.files ?? []);
    campo.value = ''; // permite escolher o mesmo arquivo de novo
    const vagas = MAXIMO_FOTOS - this.fotos().length;
    if (arquivos.length > vagas) {
      this.toast.mostrar(`Um anúncio leva até ${MAXIMO_FOTOS} fotos.`);
    }
    const novas: Foto[] = [];
    for (const arquivo of arquivos.slice(0, Math.max(0, vagas))) {
      const problema = problemaNaFoto(arquivo);
      if (problema) {
        this.toast.mostrar(problema);
        continue;
      }
      novas.push({ arquivo, previa: URL.createObjectURL(arquivo) });
    }
    this.fotos.update((atuais) => [...atuais, ...novas]);
  }

  protected removerFoto(foto: Foto): void {
    liberar(foto);
    this.fotos.update((atuais) => atuais.filter((f) => f !== foto));
  }

  /** Leva a foto para o começo: a primeira é a capa. */
  protected usarComoCapa(foto: Foto): void {
    this.fotos.update((atuais) => [foto, ...atuais.filter((f) => f !== foto)]);
  }

  /**
   * Na edição, o backend põe as fotos que ficam antes das novas. Por isso só uma foto que o anúncio já tem
   * pode virar capa; ao anunciar, qualquer uma. As novas entram sempre no fim da lista, como vão ficar.
   */
  protected podeSerCapa(foto: Foto, posicao: number): boolean {
    return posicao > 0 && (!this.edicao || 'url' in foto);
  }

  protected enderecoDa(foto: Foto): string {
    return 'url' in foto ? foto.url : foto.previa;
  }

  // ----------------------------------------------------------------- salvar

  protected salvar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.form.invalid || this.salvando() || this.carregando()) {
      this.form.markAllAsTouched();
      return;
    }
    const valores = this.form.getRawValue();
    const dados: DadosAnuncio = {
      tipo: valores.tipo,
      categoriaId: valores.categoriaId,
      titulo: valores.titulo.trim(),
      descricao: valores.descricao.trim(),
      modalidadePreco: valores.modalidadePreco,
      preco: pedePreco(valores.modalidadePreco) ? Number(valores.preco) : null,
      bolhaIds: valores.bolhaIds,
    };
    const fotos = this.fotos();
    const novas = fotos.flatMap((foto) => ('arquivo' in foto ? [foto.arquivo] : []));
    const original = this.original();
    // Na edição vão as fotos que ficam, na ordem da tela, e as novas, que o backend põe depois delas.
    const pedido = original
      ? this.servico.editar(
          original.id,
          dados,
          fotos.flatMap((foto) => ('url' in foto ? [foto.url] : [])),
          novas,
        )
      : this.servico.publicar(dados, novas);

    this.salvando.set(true);
    pedido.subscribe({
      next: (anuncio) => {
        this.toast.mostrar(original ? 'Anúncio atualizado' : 'Anúncio publicado no marketplace');
        void this.router.navigate(['/marketplace', anuncio.id]);
      },
      error: (erro: unknown) => {
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
        this.salvando.set(false);
      },
    });
  }

  protected cancelar(): void {
    const original = this.original();
    void this.router.navigate(original ? ['/marketplace', original.id] : ['/marketplace']);
  }

  // ---------------------------------------------------------------- excluir

  protected excluir(): void {
    const original = this.original();
    if (!original || this.excluindo()) {
      return;
    }
    this.excluindo.set(true);
    this.servico.excluir(original.id).subscribe({
      next: () => {
        this.toast.mostrar('Anúncio excluído');
        void this.router.navigateByUrl('/marketplace');
      },
      error: () => {
        this.excluindo.set(false);
        this.toast.mostrar('Não foi possível excluir. Tente de novo.');
      },
    });
  }

  // ------------------------------------------------------------------ apoio

  protected mostrarErro(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: Campo): string | null {
    return mensagemDeErro(this.form.controls[campo], MENSAGENS[campo]);
  }

  /** Na edição, só ficam marcadas as bolhas em que a pessoa ainda está: as outras não aceitam anúncio dela. */
  private preencher(anuncio: AnuncioDetalhe): void {
    this.original.set(anuncio);
    this.form.setValue({
      tipo: anuncio.tipo,
      titulo: anuncio.titulo,
      categoriaId: anuncio.categoria.id,
      descricao: anuncio.descricao ?? '',
      modalidadePreco: anuncio.modalidadePreco ?? 'A_COMBINAR',
      preco: anuncio.preco,
      bolhaIds: anuncio.bolhas.filter((b) => b.membro).map((b) => b.id),
    });
    this.fotos.set(anuncio.imagens.map((url) => ({ url })));
    this.carregando.set(false);
  }
}

function liberar(foto: Foto): void {
  if ('arquivo' in foto) {
    URL.revokeObjectURL(foto.previa);
  }
}
