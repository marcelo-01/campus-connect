import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  DestroyRef,
  Signal,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { BolhaService, recuoPorNivel } from '../../bolhas/bolha.service';
import { ConexaoService } from '../../conexoes/conexao.service';
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
  /** O número no canto do item (a `.badge` do MVP). Some quando é zero. */
  contador?: Signal<number>;
  /** Como o número é lido por leitores de tela: "2 pedidos recebidos". */
  descreverContador?: (total: number) => string;
}

/**
 * O `Sidebar` do MVP. Os itens de telas que ainda não existem mostram "em breve", como o MVP fazia com
 * Notificações e Salvos. As contagens fictícias do MVP (o "3" em Mensagens, o "Novo") e os atalhos de
 * hashtag saíram: aqui só aparece dado real, como os pedidos de conexão esperando resposta.
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
  private readonly conexoes = inject(ConexaoService);
  protected readonly bolhas = inject(BolhaService);

  protected readonly itens: ItemMenu[] = [
    { rotulo: 'Início', icone: 'inicio', rota: '/', exato: true },
    { rotulo: 'Explorar bolhas', icone: 'bolhas', rota: '/bolhas', exato: true },
    { rotulo: 'Marketplace', icone: 'marketplace' },
    { rotulo: 'Mensagens', icone: 'mensagens' },
    { rotulo: 'Notificações', icone: 'notificacoes' },
    { rotulo: 'Salvos', icone: 'salvos' },
    {
      // Sem `exato`: com ele o item apagaria em '/conexoes?aba=enviados'.
      rotulo: 'Conexões',
      icone: 'conexoes',
      rota: '/conexoes',
      contador: this.conexoes.totalRecebidos,
      descreverContador: (total) => `${total} ${total === 1 ? 'pedido recebido' : 'pedidos recebidos'}`,
    },
    { rotulo: 'Perfil', icone: 'perfil', rota: '/perfil' },
  ];

  protected readonly administrador = computed(() => this.auth.usuario()?.administrador === true);
  protected readonly recuo = recuoPorNivel;

  /** Só a troca de conta recarrega as listas; editar o próprio nome não precisa. */
  private readonly usuarioId = computed(() => this.auth.usuario()?.id);

  constructor() {
    effect(() => {
      if (this.usuarioId()) {
        untracked(() => {
          this.bolhas.recarregarMinhas();
          this.conexoes.recarregarMinhas();
        });
      }
    });

    // O contador não é em tempo real: ele se atualiza também quando a pessoa volta para a aba do navegador.
    const documento = inject(DOCUMENT);
    const aoVoltarParaAAba = () => {
      if (documento.visibilityState === 'visible') {
        this.conexoes.recarregarMinhas();
      }
    };
    documento.addEventListener('visibilitychange', aoVoltarParaAAba);
    inject(DestroyRef).onDestroy(() => documento.removeEventListener('visibilitychange', aoVoltarParaAAba));
  }

  /** "Conexões, 2 pedidos recebidos" para leitores de tela; sem número, fica o próprio texto do item. */
  protected rotuloAcessivel(item: ItemMenu): string | null {
    const total = item.contador?.() ?? 0;
    return total && item.descreverContador ? `${item.rotulo}, ${item.descreverContador(total)}` : null;
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
