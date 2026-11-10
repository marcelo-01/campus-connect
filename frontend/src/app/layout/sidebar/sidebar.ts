import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { BolhaService, recuoPorNivel } from '../../bolhas/bolha.service';
import { BolhaMarca } from '../../shared/bolha-marca';
import { Icone, NomeIcone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';

interface ItemMenu {
  rotulo: string;
  icone: NomeIcone;
  /** Rota pronta; sem ela, o item avisa que a tela vem depois. */
  rota?: string;
  /** Só fica ativo na rota exata: "Explorar bolhas" não acende junto com a página de uma bolha. */
  exato?: boolean;
}

/**
 * O `Sidebar` do MVP. Os itens de telas que ainda não existem mostram "em breve", como o MVP fazia com
 * Notificações e Salvos. As contagens fictícias do MVP (o "3" em Mensagens, o "Novo") e os atalhos de
 * hashtag saíram: aqui só aparece dado real.
 *
 * "Minhas bolhas" desenha a árvore como o MVP: cada bolha logo abaixo da que a contém, com recuo. O "+"
 * de nova bolha só aparece para administradores.
 */
@Component({
  selector: 'app-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icone, BolhaMarca],
  styles: ':host { display: contents; }',
  templateUrl: './sidebar.html',
})
export class Sidebar {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly bolhas = inject(BolhaService);

  protected readonly itens: ItemMenu[] = [
    { rotulo: 'Início', icone: 'inicio', rota: '/', exato: true },
    { rotulo: 'Explorar bolhas', icone: 'bolhas', rota: '/bolhas', exato: true },
    { rotulo: 'Marketplace', icone: 'marketplace' },
    { rotulo: 'Mensagens', icone: 'mensagens' },
    { rotulo: 'Notificações', icone: 'notificacoes' },
    { rotulo: 'Salvos', icone: 'salvos' },
    { rotulo: 'Perfil', icone: 'perfil', rota: '/perfil' },
  ];

  protected readonly administrador = computed(() => this.auth.usuario()?.administrador === true);
  protected readonly recuo = recuoPorNivel;

  /** Só a troca de conta recarrega a lista; editar o próprio nome não precisa. */
  private readonly usuarioId = computed(() => this.auth.usuario()?.id);

  constructor() {
    effect(() => {
      if (this.usuarioId()) {
        untracked(() => this.bolhas.recarregarMinhas());
      }
    });
  }

  protected novaBolha(): void {
    void this.router.navigateByUrl('/bolhas/nova');
  }

  protected emBreve(recurso: string): void {
    this.toast.mostrar(`${recurso} — em breve`);
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
    await this.router.navigateByUrl('/entrar');
  }
}
