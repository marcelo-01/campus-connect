import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { Perfil, PerfilService, mesEAno } from '../../perfil/perfil.service';
import { Icone } from '../../shared/icone';
import {
  ConexaoService,
  MinhasConexoes,
  PessoaNaConexao,
  SituacaoConexao,
  casaComBuscaPessoa,
  conexoesPorExtenso,
  primeiroNome,
} from '../conexao.service';
import { PessoaLinha } from '../pessoa-linha';

type Aba = keyof MinhasConexoes;

const ABAS: readonly Aba[] = ['conexoes', 'recebidos', 'enviados'];

/** A situação de quem está em cada aba. Quem muda de situação depois de uma ação sai da aba na hora. */
const SITUACAO_DA_ABA: Record<Aba, SituacaoConexao> = {
  conexoes: 'CONECTADOS',
  recebidos: 'PEDIDO_RECEBIDO',
  enviados: 'PEDIDO_ENVIADO',
};

function ehAba(valor: string | null): valor is Aba {
  return (ABAS as readonly (string | null)[]).includes(valor);
}

interface OutraPessoa {
  perfil: Perfil;
  conexoes: PessoaNaConexao[];
}

/**
 * O `ConnectionsScreen` do MVP com o modelo de conexões. As abas "Seguidores" e "Seguindo" deram lugar aos pedidos
 * recebidos e enviados, que só a própria pessoa vê. Nas conexões de outra pessoa aparece só a lista.
 *
 * Em `/conexoes` a página é de quem está logado; `?aba=recebidos` ou `?aba=enviados` abre direto na aba. Sem o
 * parâmetro, abre nos recebidos quando há pedido esperando resposta. Em `/perfil/:username/conexoes` a página é de
 * outra pessoa (a própria é levada para `/conexoes` pelo guard).
 */
@Component({
  selector: 'app-conexoes-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icone, PessoaLinha],
  styles: ':host { display: contents; }',
  templateUrl: './conexoes-pagina.html',
})
export class ConexoesPagina {
  private readonly auth = inject(AuthService);
  private readonly perfis = inject(PerfilService);
  private readonly conexoes = inject(ConexaoService);
  private readonly rota = inject(ActivatedRoute);

  private readonly username = toSignal(this.rota.paramMap.pipe(map((p) => p.get('username'))), {
    requireSync: true,
  });
  private readonly abaDaUrl = toSignal(this.rota.queryParamMap.pipe(map((q) => q.get('aba'))), {
    requireSync: true,
  });

  /** Sem `:username` na rota, a página é de quem está logado e mostra também os pedidos. */
  protected readonly propria = computed(() => this.username() === null);
  private readonly outra = signal<OutraPessoa | null>(null);
  protected readonly naoEncontrado = signal(false);

  protected readonly dono = computed(() =>
    this.propria() ? this.auth.usuario() : (this.outra()?.perfil ?? null),
  );

  private readonly carregou = computed(() => this.conexoes.minhas() !== null);

  /** Muda com o `?aba` da URL e quando as listas chegam; clicar numa aba muda só aqui. */
  protected readonly aba = linkedSignal<{ pedida: string | null; carregou: boolean }, Aba>({
    source: () => ({ pedida: this.abaDaUrl(), carregou: this.carregou() }),
    computation: ({ pedida }) => {
      if (ehAba(pedida)) {
        return pedida;
      }
      return untracked(() => this.conexoes.totalRecebidos()) > 0 ? 'recebidos' : 'conexoes';
    },
  });

  /** Quem acabou de mudar de situação e já saiu da aba. Zera quando as listas recarregam. */
  private readonly sairam = linkedSignal<MinhasConexoes | null, ReadonlySet<string>>({
    source: this.conexoes.minhas,
    computation: () => new Set<string>(),
  });

  protected readonly totais = computed(() => {
    const minhas = this.conexoes.minhas();
    if (!minhas) {
      return null;
    }
    const sairam = this.sairam();
    const contar = (lista: PessoaNaConexao[]) => lista.filter((p) => !sairam.has(p.id)).length;
    return {
      conexoes: contar(minhas.conexoes),
      recebidos: contar(minhas.recebidos),
      enviados: contar(minhas.enviados),
    };
  });

  /** A lista da aba atual. `null` enquanto carrega. */
  protected readonly pessoas = computed<PessoaNaConexao[] | null>(() => {
    if (!this.propria()) {
      return this.outra()?.conexoes ?? null;
    }
    const minhas = this.conexoes.minhas();
    if (!minhas) {
      return null;
    }
    const sairam = this.sairam();
    return minhas[this.aba()].filter((p) => !sairam.has(p.id));
  });

  protected readonly busca = signal('');
  protected readonly filtradas = computed(() =>
    (this.pessoas() ?? []).filter((pessoa) => casaComBuscaPessoa(pessoa, this.busca())),
  );

  protected readonly voltarPara = computed(() => {
    const username = this.username();
    return username ? ['/perfil', username] : ['/perfil'];
  });

  protected readonly conexoesPorExtenso = conexoesPorExtenso;
  protected readonly primeiroNome = primeiroNome;

  constructor() {
    this.rota.paramMap
      .pipe(
        map((parametros) => parametros.get('username')),
        tap(() => {
          this.outra.set(null);
          this.naoEncontrado.set(false);
          this.busca.set('');
          // Os botões de cada pessoa vêm destas listas: melhor tê-las frescas ao abrir a página.
          this.conexoes.recarregarMinhas();
        }),
        switchMap((username) => (username ? this.carregarOutra(username) : of(null))),
        takeUntilDestroyed(),
      )
      .subscribe((outra) => this.outra.set(outra));
  }

  protected digitar(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
  }

  /** "conexão desde fev 2026", ou "pedido em fev 2026" nas abas de pedidos. */
  protected detalhe(pessoa: PessoaNaConexao): string {
    const quando = mesEAno(pessoa.desde);
    return this.propria() && this.aba() !== 'conexoes' ? `pedido em ${quando}` : `conexão desde ${quando}`;
  }

  /** Na própria página, quem aceitou, recusou, cancelou ou desfez sai da aba sem esperar a recarga. */
  protected registrarMudanca(pessoa: PessoaNaConexao, situacao: SituacaoConexao): void {
    if (this.propria() && situacao !== SITUACAO_DA_ABA[this.aba()]) {
      this.sairam.update((atuais) => new Set(atuais).add(pessoa.id));
    }
  }

  private carregarOutra(username: string): Observable<OutraPessoa | null> {
    return this.perfis.buscar(username).pipe(
      switchMap((perfil) =>
        this.conexoes.doUsuario(perfil.id).pipe(map((conexoes) => ({ perfil, conexoes }))),
      ),
      catchError((erro: unknown) => {
        if (erro instanceof HttpErrorResponse && erro.status === 404) {
          this.naoEncontrado.set(true);
        }
        return of(null);
      }),
    );
  }
}
