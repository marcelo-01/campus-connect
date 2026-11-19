import { DOCUMENT, DestroyRef, Injectable, InjectionToken, computed, effect, inject, signal } from '@angular/core';

export type Tema = 'claro' | 'escuro' | 'automatico';

export type CorDestaque = 'lavanda' | 'azul' | 'turquesa' | 'verde' | 'rosa' | 'tangerina';

export const TEMAS: readonly { valor: Tema; rotulo: string }[] = [
  { valor: 'claro', rotulo: 'Claro' },
  { valor: 'escuro', rotulo: 'Escuro' },
  { valor: 'automatico', rotulo: 'Automático' },
];

/** A ordem do seletor. O tom de cada uma fica no `styles.css`, na seção "Aparência". */
export const CORES: readonly { valor: CorDestaque; rotulo: string }[] = [
  { valor: 'lavanda', rotulo: 'Lavanda' },
  { valor: 'azul', rotulo: 'Azul' },
  { valor: 'turquesa', rotulo: 'Turquesa' },
  { valor: 'verde', rotulo: 'Verde' },
  { valor: 'rosa', rotulo: 'Rosa' },
  { valor: 'tangerina', rotulo: 'Tangerina' },
];

/**
 * Onde a escolha fica guardada. O script do `index.html` lê esta mesma chave antes de o Angular subir, para a
 * página não abrir clara e depois escurecer.
 */
export const CHAVE_APARENCIA = 'campusconnect.aparencia';

/** O `localStorage`, ou `null` quando o navegador o bloqueia (alguns modos anônimos). */
export const ARMAZENAMENTO = new InjectionToken<Storage | null>('ARMAZENAMENTO', {
  factory: () => {
    try {
      return globalThis.localStorage ?? null;
    } catch {
      return null;
    }
  },
});

/** A consulta que diz se o aparelho está no tema escuro; `null` onde não existe `matchMedia`. */
export const TEMA_ESCURO_DO_SISTEMA = new InjectionToken<MediaQueryList | null>('TEMA_ESCURO_DO_SISTEMA', {
  factory: () => globalThis.matchMedia?.('(prefers-color-scheme: dark)') ?? null,
});

interface Preferencia {
  tema: Tema;
  cor: CorDestaque;
}

const PADRAO: Preferencia = { tema: 'automatico', cor: 'lavanda' };

/**
 * O tema (claro, escuro ou o do aparelho) e a cor de destaque, como no X. A escolha fica no navegador, não na
 * conta: vale para todas as contas daquele navegador, inclusive na tela de login.
 *
 * O serviço põe `data-theme` (`light` ou `dark`) e `data-accent` no `<html>`, e o CSS faz o resto. Com o tema
 * automático, ele acompanha o aparelho quando este troca de tema com a página aberta. As outras abas abertas
 * recebem a escolha pelo evento `storage`.
 */
@Injectable({ providedIn: 'root' })
export class AparenciaService {
  private readonly armazenamento = inject(ARMAZENAMENTO);
  private readonly preferencia = signal(this.ler());
  private readonly sistemaEscuro = signal(false);

  readonly tema = computed(() => this.preferencia().tema);
  readonly cor = computed(() => this.preferencia().cor);

  /** O tema que está na tela: no automático, o do aparelho. */
  readonly temaAplicado = computed<'light' | 'dark'>(() => {
    const tema = this.tema();
    const escuro = tema === 'automatico' ? this.sistemaEscuro() : tema === 'escuro';
    return escuro ? 'dark' : 'light';
  });

  constructor() {
    const documento = inject(DOCUMENT);
    const destruir = inject(DestroyRef);

    const sistema = inject(TEMA_ESCURO_DO_SISTEMA);
    if (sistema) {
      this.sistemaEscuro.set(sistema.matches);
      const aoMudarOSistema = (evento: MediaQueryListEvent) => this.sistemaEscuro.set(evento.matches);
      sistema.addEventListener('change', aoMudarOSistema);
      destruir.onDestroy(() => sistema.removeEventListener('change', aoMudarOSistema));
    }

    const janela = documento.defaultView;
    if (janela) {
      const aoMudarEmOutraAba = (evento: StorageEvent) => {
        if (evento.key === CHAVE_APARENCIA || evento.key === null) {
          this.preferencia.set(this.ler());
        }
      };
      janela.addEventListener('storage', aoMudarEmOutraAba);
      destruir.onDestroy(() => janela.removeEventListener('storage', aoMudarEmOutraAba));
    }

    const raiz = documento.documentElement;
    effect(() => {
      raiz.dataset['theme'] = this.temaAplicado();
      raiz.dataset['accent'] = this.cor();
    });
  }

  escolherTema(tema: Tema): void {
    this.salvar({ ...this.preferencia(), tema });
  }

  escolherCor(cor: CorDestaque): void {
    this.salvar({ ...this.preferencia(), cor });
  }

  private salvar(preferencia: Preferencia): void {
    this.preferencia.set(preferencia);
    try {
      this.armazenamento?.setItem(CHAVE_APARENCIA, JSON.stringify(preferencia));
    } catch {
      // Sem espaço ou bloqueado: a escolha vale só até a página fechar.
    }
  }

  /** Um valor que não é mais reconhecido (uma cor que saiu, por exemplo) volta ao padrão. */
  private ler(): Preferencia {
    let salvo: Partial<Record<keyof Preferencia, unknown>> = {};
    try {
      salvo = JSON.parse(this.armazenamento?.getItem(CHAVE_APARENCIA) ?? '{}') ?? {};
    } catch {
      // JSON quebrado: fica o padrão.
    }
    return {
      tema: TEMAS.some((t) => t.valor === salvo.tema) ? (salvo.tema as Tema) : PADRAO.tema,
      cor: CORES.some((c) => c.valor === salvo.cor) ? (salvo.cor as CorDestaque) : PADRAO.cor,
    };
  }
}
