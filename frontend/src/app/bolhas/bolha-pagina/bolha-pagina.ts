import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, map, merge, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { TrilhaService } from '../../layout/trilha.service';
import { mesEAno } from '../../perfil/perfil.service';
import { Avatar } from '../../shared/avatar';
import { BolhaMarca } from '../../shared/bolha-marca';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { BolhaCartao } from '../bolha-cartao';
import {
  BolhaDetalhe,
  BolhaService,
  MembroDaBolha,
  corDaBolha,
  membrosPorExtenso,
  rotuloDoTipo,
  siglaExibida,
} from '../bolha.service';

type Aba = 'posts' | 'subbolhas' | 'membros' | 'sobre';

/**
 * A página de uma bolha. O MVP não tinha uma: clicar numa bolha só filtrava o feed. Ela junta peças que o
 * MVP já tinha: o `.bubble-hero` (estilo pronto e nunca usado), as abas do perfil, o card do Explorar
 * para as sub-bolhas e a `.person-row` das conexões para os membros. A barra do topo mostra a hierarquia
 * ("IFBA › Campus › Curso"), como o `Topbar` do MVP.
 *
 * O feed da bolha chega com as publicações; por ora a aba de posts mostra o estado vazio do MVP.
 */
@Component({
  selector: 'app-bolha-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, BolhaCartao, BolhaMarca, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './bolha-pagina.html',
})
export class BolhaPagina {
  private readonly auth = inject(AuthService);
  private readonly bolhas = inject(BolhaService);
  private readonly toast = inject(ToastService);
  private readonly trilhas = inject(TrilhaService);
  private readonly titulo = inject(Title);

  protected readonly bolha = signal<BolhaDetalhe | null>(null);
  protected readonly membros = signal<MembroDaBolha[] | null>(null);
  protected readonly naoEncontrada = signal(false);
  protected readonly aba = signal<Aba>('posts');
  /** "Entrar" ou "Sair" em andamento. */
  protected readonly agindo = signal(false);

  protected readonly administrador = computed(() => this.auth.usuario()?.administrador === true);
  protected readonly acima = computed(() => this.bolha()?.caminho.at(-1) ?? null);
  protected readonly cor = computed(() => {
    const bolha = this.bolha();
    return bolha ? corDaBolha(bolha.tipo) : null;
  });
  protected readonly sigla = computed(() => {
    const bolha = this.bolha();
    return bolha ? siglaExibida(bolha, 2) : '';
  });

  protected readonly rotuloDoTipo = rotuloDoTipo;
  protected readonly membrosPorExtenso = membrosPorExtenso;
  protected readonly mesEAno = mesEAno;

  /** Recarrega a bolha atual depois de entrar ou sair, sem mudar de aba. */
  private readonly recarregar = new Subject<void>();

  constructor() {
    const slugDaRota = inject(ActivatedRoute).paramMap.pipe(
      map((parametros) => parametros.get('slug') ?? ''),
      tap(() => {
        this.bolha.set(null);
        this.membros.set(null);
        this.naoEncontrada.set(false);
        this.aba.set('posts');
      }),
    );
    let slugAtual = '';
    merge(
      slugDaRota.pipe(tap((slug) => (slugAtual = slug))),
      this.recarregar.pipe(map(() => slugAtual)),
    )
      .pipe(
        switchMap((slug) =>
          this.bolhas.buscar(slug).pipe(
            catchError((erro: unknown) => {
              if (erro instanceof HttpErrorResponse && erro.status === 404) {
                this.naoEncontrada.set(true);
                this.trilhas.definir(['Explorar bolhas', 'Bolha não encontrada']);
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((bolha) => {
        if (bolha) {
          this.mostrar(bolha);
        }
      });
  }

  protected entrar(bolha: BolhaDetalhe): void {
    this.agir(this.bolhas.entrar(bolha.id), `Você entrou em ${bolha.nome}`);
  }

  protected sair(bolha: BolhaDetalhe): void {
    const recado = bolha.filhas.length
      ? `Você saiu de ${bolha.nome} e das bolhas dentro dela`
      : `Você saiu de ${bolha.nome}`;
    this.agir(this.bolhas.sair(bolha.id), recado);
  }

  private agir(acao: ReturnType<BolhaService['entrar']>, recado: string): void {
    this.agindo.set(true);
    acao.subscribe({
      next: () => {
        this.toast.mostrar(recado);
        this.agindo.set(false);
        this.recarregar.next();
      },
      error: () => {
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
        this.agindo.set(false);
      },
    });
  }

  private mostrar(bolha: BolhaDetalhe): void {
    this.bolha.set(bolha);
    this.trilhas.definir([...bolha.caminho.map((acima) => acima.nome), bolha.nome]);
    this.titulo.setTitle(`${bolha.nome} · CampusConnect`);
    this.bolhas.membros(bolha.id).subscribe({
      next: (membros) => this.membros.set(membros),
      error: () => this.membros.set([]),
    });
  }
}
