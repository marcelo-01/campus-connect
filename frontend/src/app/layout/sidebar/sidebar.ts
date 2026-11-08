import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { BolhaResumo, PerfilService, corDaBolha, recuoDaBolha, siglaDaBolha } from '../../perfil/perfil.service';
import { Icone, NomeIcone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';

interface ItemMenu {
  rotulo: string;
  icone: NomeIcone;
  /** Rota pronta; sem ela, o item avisa que a tela vem depois. */
  rota?: string;
}

/**
 * O `Sidebar` do MVP. Os itens de telas que ainda não existem mostram "em breve", como o MVP fazia com
 * Notificações e Salvos. As contagens fictícias do MVP (o "3" em Mensagens, o "Novo") e os atalhos de
 * hashtag saíram: aqui só aparece dado real.
 */
@Component({
  selector: 'app-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './sidebar.html',
})
export class Sidebar {
  private readonly auth = inject(AuthService);
  private readonly perfis = inject(PerfilService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly itens: ItemMenu[] = [
    { rotulo: 'Início', icone: 'inicio', rota: '/' },
    { rotulo: 'Explorar bolhas', icone: 'bolhas' },
    { rotulo: 'Marketplace', icone: 'marketplace' },
    { rotulo: 'Mensagens', icone: 'mensagens' },
    { rotulo: 'Notificações', icone: 'notificacoes' },
    { rotulo: 'Salvos', icone: 'salvos' },
    { rotulo: 'Perfil', icone: 'perfil', rota: '/perfil' },
  ];

  protected readonly bolhas = signal<BolhaResumo[] | null>(null);
  protected readonly cor = corDaBolha;
  protected readonly sigla = siglaDaBolha;
  protected readonly recuo = recuoDaBolha;

  constructor() {
    effect(() => {
      const usuario = this.auth.usuario();
      if (usuario) {
        this.perfis.bolhasDoUsuario(usuario.id).subscribe({
          next: (bolhas) => this.bolhas.set(bolhas),
          error: () => this.bolhas.set([]),
        });
      }
    });
  }

  protected emBreve(recurso: string): void {
    this.toast.mostrar(`${recurso} — em breve`);
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
    await this.router.navigateByUrl('/entrar');
  }
}
