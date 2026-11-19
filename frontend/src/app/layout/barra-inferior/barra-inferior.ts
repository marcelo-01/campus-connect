import { ChangeDetectionStrategy, Component, Signal, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ConexaoService } from '../../conexoes/conexao.service';
import { MensagemService } from '../../mensagens/mensagem.service';
import { Icone, NomeIcone } from '../../shared/icone';

interface Atalho {
  rotulo: string;
  icone: NomeIcone;
  rota: string;
  exato?: boolean;
  contador?: Signal<number>;
  /** Como o número é lido por leitores de tela: "2 pedidos recebidos". */
  descreverContador?: (total: number) => string;
}

/**
 * A barra de baixo das telas estreitas, como nos apps de celular: os atalhos que mais se usa, a um toque. O resto
 * (Salvos, as bolhas da pessoa, Sair) fica na gaveta do menu, e as Notificações e o Perfil, no sino e no avatar da
 * barra do topo.
 * Nas telas largas o CSS a esconde, e o menu lateral faz esse papel.
 *
 * Os números são os mesmos do menu lateral, que é quem os recarrega.
 */
@Component({
  selector: 'app-barra-inferior',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icone],
  styles: ':host { display: contents; }',
  template: `
    <nav class="barra-inferior" aria-label="Atalhos">
      @for (atalho of atalhos; track atalho.rota) {
        <a
          class="barra-item"
          [routerLink]="atalho.rota"
          routerLinkActive="active"
          ariaCurrentWhenActive="page"
          [routerLinkActiveOptions]="{ exact: atalho.exato === true }"
          [attr.aria-label]="rotuloAcessivel(atalho)"
        >
          <span class="barra-icone">
            <app-icone [nome]="atalho.icone" />
            @if (atalho.contador?.(); as total) {
              <span class="badge" aria-hidden="true">{{ total > 99 ? '99+' : total }}</span>
            }
          </span>
          <span class="barra-rotulo">{{ atalho.rotulo }}</span>
        </a>
      }
    </nav>
  `,
})
export class BarraInferior {
  private readonly conexoes = inject(ConexaoService);
  private readonly mensagens = inject(MensagemService);

  protected readonly atalhos: Atalho[] = [
    { rotulo: 'Início', icone: 'inicio', rota: '/', exato: true },
    // Sem `exato`: aqui o atalho acende também na página de uma bolha, que não tem outro item na barra.
    { rotulo: 'Bolhas', icone: 'bolhas', rota: '/bolhas' },
    { rotulo: 'Marketplace', icone: 'marketplace', rota: '/marketplace' },
    {
      rotulo: 'Mensagens',
      icone: 'mensagens',
      rota: '/mensagens',
      contador: this.mensagens.naoLidas,
      descreverContador: (total) => `${total} ${total === 1 ? 'conversa não lida' : 'conversas não lidas'}`,
    },
    {
      rotulo: 'Conexões',
      icone: 'conexoes',
      rota: '/conexoes',
      contador: this.conexoes.totalRecebidos,
      descreverContador: (total) => `${total} ${total === 1 ? 'pedido recebido' : 'pedidos recebidos'}`,
    },
  ];

  protected rotuloAcessivel(atalho: Atalho): string | null {
    const total = atalho.contador?.() ?? 0;
    return total && atalho.descreverContador ? `${atalho.rotulo}, ${atalho.descreverContador(total)}` : null;
  }
}
