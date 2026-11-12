import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { BolhaResumo, BolhaService, membrosPorExtenso } from '../../bolhas/bolha.service';
import { BotaoConexao } from '../../conexoes/botao-conexao';
import { ConexaoService } from '../../conexoes/conexao.service';
import { Avatar } from '../../shared/avatar';
import { BolhaMarca } from '../../shared/bolha-marca';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { Perfil, PerfilService, linkSemEsquema, mesEAno } from '../perfil.service';

type Aba = 'posts' | 'market' | 'saved' | 'about';

/**
 * O `ProfileScreen` do MVP com dados reais. Conexões e bolhas vêm do banco. "Seguidores" e "seguindo" saíram:
 * o modelo de dados só tem conexões. No perfil de outra pessoa, o "Seguir" do MVP virou o botão de conexão, e o
 * número de conexões abre a lista. As abas de posts, marketplace e salvos mostram o estado vazio até cada
 * função chegar.
 *
 * Sem `:username` na rota, mostra o perfil de quem está logado.
 */
@Component({
  selector: 'app-perfil-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, BolhaMarca, BotaoConexao, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './perfil-pagina.html',
})
export class PerfilPagina {
  private readonly auth = inject(AuthService);
  private readonly perfis = inject(PerfilService);
  private readonly bolhasService = inject(BolhaService);
  private readonly conexoes = inject(ConexaoService);
  private readonly toast = inject(ToastService);

  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly bolhas = signal<BolhaResumo[]>([]);
  protected readonly totalConexoes = signal(0);
  protected readonly naoEncontrado = signal(false);
  protected readonly aba = signal<Aba>('posts');

  protected readonly membros = membrosPorExtenso;
  protected readonly linkSemEsquema = linkSemEsquema;
  protected readonly membroDesde = computed(() => {
    const perfil = this.perfil();
    return perfil ? mesEAno(perfil.membroDesde) : '';
  });
  protected readonly primeiroNome = computed(() => this.perfil()?.nomeCompleto.split(/\s+/)[0] ?? '');

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(
        map((parametros) => parametros.get('username') ?? this.auth.usuario()?.username ?? ''),
        tap(() => {
          this.perfil.set(null);
          this.naoEncontrado.set(false);
          this.aba.set('posts');
        }),
        switchMap((username) =>
          this.perfis.buscar(username).pipe(
            switchMap((perfil) =>
              forkJoin({
                perfil: of(perfil),
                bolhas: this.bolhasService.doUsuario(perfil.id).pipe(catchError(() => of([]))),
                conexoes: this.conexoes.total(perfil.id).pipe(catchError(() => of(0))),
              }),
            ),
            catchError((erro: unknown) => {
              if (erro instanceof HttpErrorResponse && erro.status === 404) {
                this.naoEncontrado.set(true);
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((dados) => {
        if (dados) {
          this.bolhas.set(dados.bolhas);
          this.totalConexoes.set(dados.conexoes);
          this.perfil.set(dados.perfil);
          if (!dados.perfil.proprio) {
            // O botão de conexão vem das listas: a outra pessoa pode ter respondido desde a última recarga.
            this.conexoes.recarregarMinhas();
          }
        }
      });
  }

  /** Depois de conectar ou desfazer, o número do perfil muda. Se a tela já mostra outro perfil, nada muda. */
  protected recarregarTotal(perfil: Perfil): void {
    this.conexoes.total(perfil.id).subscribe({
      next: (total) => {
        if (this.perfil()?.id === perfil.id) {
          this.totalConexoes.set(total);
        }
      },
    });
  }

  protected async compartilhar(perfil: Perfil): Promise<void> {
    const endereco = `${location.origin}/perfil/${perfil.username}`;
    try {
      await navigator.clipboard.writeText(endereco);
      this.toast.mostrar('Link do perfil copiado');
    } catch {
      this.toast.mostrar(endereco);
    }
  }
}
