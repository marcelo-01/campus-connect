import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { ProblemaApi } from '../../auth/sessao';
import { BolhaMarca } from '../../shared/bolha-marca';
import { aplicarErroDaApi, mensagemDeErro } from '../../shared/formulario';
import { Icone } from '../../shared/icone';
import { problemaNaImagem } from '../../shared/imagem';
import { ToastService } from '../../shared/toast.service';
import {
  BolhaDetalhe,
  BolhaResumo,
  BolhaService,
  TIPOS_DE_BOLHA,
  TipoBolha,
  caminhoCompleto,
  exigeBolhaAcima,
  membrosPorExtenso,
  paisPermitidos,
  rotuloDoTipo,
} from '../bolha.service';

type Campo = 'nome' | 'sigla' | 'tipo' | 'bolhaPaiId' | 'descricao' | 'endereco';

const TAMANHO_DESCRICAO = 500;

const MENSAGENS: Record<Campo, Record<string, string>> = {
  nome: { required: 'Dê um nome à bolha.', maxlength: 'Use no máximo 120 caracteres.' },
  sigla: { maxlength: 'Use no máximo 10 caracteres.' },
  tipo: { required: 'Escolha o tipo da bolha.' },
  bolhaPaiId: {},
  descricao: { maxlength: `A descrição pode ter até ${TAMANHO_DESCRICAO} caracteres.` },
  endereco: { maxlength: 'Use no máximo 200 caracteres.' },
};

/** O que falta quando o tipo exige bolha acima e nenhuma foi escolhida; o mesmo texto do backend. */
const BOLHA_ACIMA_OBRIGATORIA: Partial<Record<TipoBolha, string>> = {
  CAMPUS: 'Um campus fica dentro de uma bolha geral.',
  CURSO: 'Um curso fica dentro de um campus.',
};

const DICAS: Record<TipoBolha, string> = {
  GERAL: 'O topo da hierarquia, como a universidade inteira. Fica sem bolha acima.',
  CAMPUS: 'Uma unidade da universidade. Fica dentro de uma bolha geral.',
  CURSO: 'Fica dentro de um campus.',
  COMUNIDADE: 'Um grupo de interesse. Pode ficar no topo ou dentro de outra bolha.',
};

/**
 * Criar e editar bolha, só para administradores. O MVP não tinha essa tela; o layout é o do
 * `CreateServiceScreen` (formulário à esquerda, pré-visualização à direita). Tipo e bolha acima só se
 * escolhem na criação. A foto escolhida é enviada junto ao salvar.
 *
 * Na edição, a exclusão fica num card no fim, com confirmação ali mesmo: o MVP não tem modal.
 */
@Component({
  selector: 'app-bolha-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, BolhaMarca, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './bolha-form.html',
})
export class BolhaForm {
  private readonly servico = inject(BolhaService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Na edição, a bolha como está no banco; na criação, `null`. */
  protected readonly original = signal<BolhaDetalhe | null>(null);
  protected readonly edicao = !!inject(ActivatedRoute).snapshot.paramMap.get('slug');
  protected readonly todas = signal<BolhaResumo[]>([]);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);

  protected readonly fotoNova = signal<File | null>(null);
  protected readonly previaFoto = signal<string | null>(null);
  protected readonly erroFoto = signal<string | null>(null);

  /** Se a foto falhou depois de a bolha ser salva, o aviso já foi dado e o "salvo" não aparece. */
  private fotoFalhou = false;

  protected readonly confirmandoExclusao = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroExclusao = signal<string | null>(null);

  protected readonly tipos = TIPOS_DE_BOLHA;
  protected readonly tamanhoDescricao = TAMANHO_DESCRICAO;
  protected readonly rotuloDoTipo = rotuloDoTipo;
  protected readonly membrosPorExtenso = membrosPorExtenso;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    sigla: ['', Validators.maxLength(10)],
    tipo: ['' as TipoBolha | '', Validators.required],
    bolhaPaiId: [''],
    descricao: ['', Validators.maxLength(TAMANHO_DESCRICAO)],
    endereco: ['', Validators.maxLength(200)],
  });

  /** O formulário inteiro como signal, para a pré-visualização e as opções de bolha acima. */
  private readonly valores = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.form.getRawValue()),
    ),
    { requireSync: true },
  );

  protected readonly tipo = computed(() => this.valores().tipo || null);
  protected readonly dica = computed(() => {
    const tipo = this.tipo();
    return tipo ? DICAS[tipo] : null;
  });
  protected readonly opcoesAcima = computed(() => {
    const tipo = this.tipo();
    return tipo
      ? paisPermitidos(tipo, this.todas()).map((b) => ({
          id: b.id,
          rotulo: caminhoCompleto(b, this.todas()),
        }))
      : [];
  });
  protected readonly mostraBolhaAcima = computed(() => !!this.tipo() && this.tipo() !== 'GERAL');
  /** Na edição, a bolha acima só aparece como texto: "IFBA › Campus Eunápolis". */
  protected readonly caminhoOriginal = computed(
    () =>
      this.original()
        ?.caminho.map((acima) => acima.nome)
        .join(' › ') || 'Nenhuma: fica no topo',
  );

  /** O card do Explorar com o que está no formulário. */
  protected readonly previa = computed(() => {
    const valores = this.valores();
    return {
      nome: valores.nome.trim() || 'Nome da bolha',
      sigla: valores.sigla.trim() || null,
      tipo: (valores.tipo || 'COMUNIDADE') as TipoBolha,
      fotoUrl: this.previaFoto() ?? this.original()?.fotoUrl ?? null,
      descricao: valores.descricao.trim(),
    };
  });

  constructor() {
    const slug = inject(ActivatedRoute).snapshot.paramMap.get('slug');
    if (slug) {
      this.form.controls.tipo.disable();
      this.form.controls.bolhaPaiId.disable();
      this.servico.buscar(slug).subscribe({
        next: (bolha) => this.preencher(bolha),
        error: (erro: unknown) => {
          this.carregando.set(false);
          this.erroGeral.set(
            erro instanceof HttpErrorResponse && erro.status === 404
              ? 'Essa bolha não existe mais.'
              : 'Não foi possível carregar a bolha. Recarregue a página.',
          );
        },
      });
    } else {
      this.servico.todas().subscribe({
        next: (todas) => {
          this.todas.set(todas);
          this.carregando.set(false);
        },
        error: () => {
          this.carregando.set(false);
          this.erroGeral.set(
            'Não foi possível carregar as bolhas existentes. Recarregue a página.',
          );
        },
      });
      // Trocar o tipo pode tornar a bolha acima escolhida inválida, e muda se ela é obrigatória.
      this.form.controls.tipo.valueChanges
        .pipe(takeUntilDestroyed())
        .subscribe((tipo) => this.ajustarBolhaAcima(tipo));
    }
    inject(DestroyRef).onDestroy(() => this.trocarPrevia(null));
  }

  // ------------------------------------------------------------------- foto

  protected escolherFoto(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = ''; // permite escolher o mesmo arquivo de novo
    if (!arquivo) {
      return;
    }
    const problema = problemaNaImagem(arquivo);
    this.erroFoto.set(problema);
    if (problema) {
      return;
    }
    this.fotoNova.set(arquivo);
    this.trocarPrevia(URL.createObjectURL(arquivo));
  }

  protected descartarFoto(): void {
    this.fotoNova.set(null);
    this.trocarPrevia(null);
  }

  private trocarPrevia(url: string | null): void {
    const anterior = this.previaFoto();
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    this.previaFoto.set(url);
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
    const campos = {
      nome: valores.nome.trim(),
      sigla: valores.sigla.trim(),
      descricao: valores.descricao.trim(),
      endereco: valores.endereco.trim(),
    };
    const original = this.original();
    const pedido: Observable<BolhaDetalhe> = original
      ? this.servico.atualizar(original.id, campos)
      : this.servico.criar({
          ...campos,
          tipo: valores.tipo as TipoBolha,
          bolhaPaiId: valores.bolhaPaiId || null,
        });

    this.salvando.set(true);
    pedido.pipe(switchMap((bolha) => this.enviarFoto(bolha))).subscribe({
      next: (bolha) => {
        if (!this.fotoFalhou) {
          this.toast.mostrar(original ? 'Bolha atualizada' : 'Bolha criada');
        }
        void this.router.navigate(['/bolhas', bolha.slug]);
      },
      error: (erro: unknown) => {
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
        this.salvando.set(false);
      },
    });
  }

  /** Se a foto falhar, a bolha já está salva: avisa e segue para a página dela. */
  private enviarFoto(bolha: BolhaDetalhe): Observable<BolhaDetalhe> {
    const foto = this.fotoNova();
    if (!foto) {
      return of(bolha);
    }
    return this.servico.trocarFoto(bolha.id, foto).pipe(
      catchError((erro: unknown) => {
        const problema =
          erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
        this.fotoFalhou = true;
        this.toast.mostrar(
          `A bolha foi salva, mas a foto não: ${problema?.erros?.['arquivo'] ?? 'tente enviar de novo.'}`,
        );
        return of(bolha);
      }),
    );
  }

  protected cancelar(): void {
    const original = this.original();
    void this.router.navigate(original ? ['/bolhas', original.slug] : ['/bolhas']);
  }

  // ---------------------------------------------------------------- excluir

  protected excluir(): void {
    const original = this.original();
    if (!original || this.excluindo()) {
      return;
    }
    this.excluindo.set(true);
    this.erroExclusao.set(null);
    this.servico.excluir(original.id).subscribe({
      next: () => {
        this.toast.mostrar(`${original.nome} foi excluída`);
        void this.router.navigateByUrl('/bolhas');
      },
      error: (erro: unknown) => {
        const problema =
          erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
        this.erroExclusao.set(problema?.detail ?? 'Não foi possível excluir. Tente de novo.');
        this.excluindo.set(false);
      },
    });
  }

  // ------------------------------------------------------------------ apoio

  protected mostrarErro(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: Campo): string | null {
    const controle = this.form.controls[campo];
    if (campo === 'bolhaPaiId' && controle.hasError('required')) {
      return BOLHA_ACIMA_OBRIGATORIA[this.tipo() ?? 'CAMPUS'] ?? null;
    }
    return mensagemDeErro(controle, MENSAGENS[campo]);
  }

  private preencher(bolha: BolhaDetalhe): void {
    this.original.set(bolha);
    this.form.setValue({
      nome: bolha.nome,
      sigla: bolha.sigla ?? '',
      tipo: bolha.tipo,
      bolhaPaiId: bolha.caminho.at(-1)?.id ?? '',
      descricao: bolha.descricao ?? '',
      endereco: bolha.endereco ?? '',
    });
    this.carregando.set(false);
  }

  private ajustarBolhaAcima(tipo: TipoBolha | ''): void {
    const controle = this.form.controls.bolhaPaiId;
    controle.setValidators(tipo && exigeBolhaAcima(tipo) ? Validators.required : null);
    const permitidas = tipo ? paisPermitidos(tipo, this.todas()) : [];
    if (controle.value && !permitidas.some((b) => b.id === controle.value)) {
      controle.setValue('');
    }
    controle.updateValueAndValidity();
  }
}
