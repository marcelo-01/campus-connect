import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import {
  BolhaResumo,
  Perfil,
  PerfilService,
  corDaBolha,
  linkSemEsquema,
  mesEAno,
  siglaDaBolha,
} from '../perfil.service';

type Aba = 'posts' | 'market' | 'saved' | 'about';

/**
 * O `ProfileScreen` do MVP com dados reais. Conexões e bolhas vêm do banco (zero até essas funções
 * existirem). "Seguidores" e "seguindo" saíram: o modelo de dados só tem conexões. As abas de posts,
 * marketplace e salvos mostram o estado vazio até cada função chegar.
 *
 * Sem `:username` na rota, mostra o perfil de quem está logado.
 */
@Component({
  selector: 'app-perfil-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './perfil-pagina.html',
})
export class PerfilPagina {
  private readonly auth = inject(AuthService);
  private readonly perfis = inject(PerfilService);
  private readonly toast = inject(ToastService);

  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly bolhas = signal<BolhaResumo[]>([]);
  protected readonly conexoes = signal(0);
  protected readonly naoEncontrado = signal(false);
  protected readonly aba = signal<Aba>('posts');

  protected readonly cor = corDaBolha;
  protected readonly sigla = siglaDaBolha;
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
                bolhas: this.perfis.bolhasDoUsuario(perfil.id).pipe(catchError(() => of([]))),
                conexoes: this.perfis.totalDeConexoes(perfil.id).pipe(catchError(() => of(0))),
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
          this.conexoes.set(dados.conexoes);
          this.perfil.set(dados.perfil);
        }
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

  protected emBreve(recurso: string): void {
    this.toast.mostrar(`${recurso} — em breve`);
  }
}
