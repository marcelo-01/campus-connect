import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { BolhaMarca } from '../../shared/bolha-marca';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { BolhaCartao } from '../bolha-cartao';
import {
  BolhaResumo,
  BolhaService,
  TipoBolha,
  casaComBusca,
  membrosPorExtenso,
} from '../bolha.service';

type Filtro = 'TODAS' | TipoBolha;

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'TODAS', rotulo: 'Toda a rede' },
  { valor: 'GERAL', rotulo: 'Gerais' },
  { valor: 'CAMPUS', rotulo: 'Campus' },
  { valor: 'CURSO', rotulo: 'Cursos' },
  { valor: 'COMUNIDADE', rotulo: 'Comunidades' },
];

/**
 * O `ExploreScreen` do MVP com dados reais e sem nome de instituição fixo no texto. Sem busca nem filtro,
 * mostra como o MVP: cada bolha geral em destaque, depois os campus, os cursos e as comunidades. Com
 * busca ou filtro, tudo vira uma grade só. Os chips do MVP eram enfeite; aqui filtram pelo tipo.
 */
@Component({
  selector: 'app-explorar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BolhaCartao, BolhaMarca, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './explorar.html',
})
export class Explorar {
  private readonly auth = inject(AuthService);
  private readonly servico = inject(BolhaService);
  private readonly toast = inject(ToastService);

  protected readonly filtros = FILTROS;
  protected readonly administrador = computed(() => this.auth.usuario()?.administrador === true);

  protected readonly bolhas = signal<BolhaResumo[] | null>(null);
  protected readonly erro = signal(false);
  protected readonly busca = signal('');
  protected readonly filtro = signal<Filtro>('TODAS');
  /** Bolha cujo "Entrar" está em andamento. */
  protected readonly entrando = signal<string | null>(null);

  private readonly porId = computed(() => new Map((this.bolhas() ?? []).map((b) => [b.id, b])));

  protected readonly filtrando = computed(
    () => this.busca().trim() !== '' || this.filtro() !== 'TODAS',
  );
  protected readonly resultados = computed(() =>
    (this.bolhas() ?? []).filter(
      (b) =>
        (this.filtro() === 'TODAS' || b.tipo === this.filtro()) && casaComBusca(b, this.busca()),
    ),
  );
  protected readonly gerais = computed(() => this.doTipo('GERAL'));
  protected readonly campi = computed(() => this.doTipo('CAMPUS'));
  protected readonly cursos = computed(() => this.doTipo('CURSO'));
  protected readonly comunidades = computed(() => this.doTipo('COMUNIDADE'));

  protected readonly membros = membrosPorExtenso;

  constructor() {
    this.carregar();
  }

  /** "2 campus", para o destaque de uma bolha geral. */
  protected campusDe(geral: BolhaResumo): number {
    return (this.bolhas() ?? []).filter((b) => b.bolhaPaiId === geral.id && b.tipo === 'CAMPUS')
      .length;
  }

  protected nomeAcima(bolha: BolhaResumo): string | undefined {
    return bolha.bolhaPaiId ? this.porId().get(bolha.bolhaPaiId)?.nome : undefined;
  }

  protected entrar(bolha: BolhaResumo): void {
    this.entrando.set(bolha.id);
    this.servico.entrar(bolha.id).subscribe({
      next: () => {
        this.toast.mostrar(`Você entrou em ${bolha.nome}`);
        this.entrando.set(null);
        this.carregar();
      },
      error: () => {
        this.toast.mostrar('Não foi possível entrar. Tente de novo.');
        this.entrando.set(null);
      },
    });
  }

  protected digitar(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
  }

  private carregar(): void {
    this.servico.todas().subscribe({
      next: (bolhas) => {
        this.erro.set(false);
        this.bolhas.set(bolhas);
      },
      error: () => this.erro.set(true),
    });
  }

  private doTipo(tipo: TipoBolha): BolhaResumo[] {
    return (this.bolhas() ?? []).filter((b) => b.tipo === tipo);
  }
}
