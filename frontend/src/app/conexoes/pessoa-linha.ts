import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Avatar } from '../shared/avatar';
import { BotaoConexao } from './botao-conexao';
import { SituacaoConexao } from './conexao.service';

/** O que a linha mostra de uma pessoa. Serve para membros de bolha e para pessoas nas listas de conexões. */
export interface PessoaDaLinha {
  id: string;
  username: string;
  nomeCompleto: string;
  fotoUrl: string | null;
}

/**
 * A `PersonRow` do MVP: avatar, nome e @username, com o botão de conexão à direita. O avatar e o nome levam ao
 * perfil por um link interno, porque um botão dentro de um link é HTML inválido.
 */
@Component({
  selector: 'app-pessoa-linha',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, BotaoConexao],
  styles: ':host { display: contents; }',
  template: `
    <div class="person-row">
      <a class="person-link" [routerLink]="['/perfil', pessoa().username]">
        <app-avatar [nome]="pessoa().nomeCompleto" [fotoUrl]="pessoa().fotoUrl" [tamanho]="tamanhoAvatar()" />
        <div class="person-info">
          <div class="name">{{ pessoa().nomeCompleto }}</div>
          <div class="handle">
            &#64;{{ pessoa().username }}
            @if (detalhe()) {
              · {{ detalhe() }}
            }
          </div>
        </div>
      </a>
      <app-botao-conexao [pessoa]="pessoa()" compacto (mudou)="mudou.emit($event)" />
    </div>
  `,
})
export class PessoaLinha {
  readonly pessoa = input.required<PessoaDaLinha>();
  /** Texto depois do @username, como "entrou em fev 2026". */
  readonly detalhe = input('');
  /** O MVP usa `lg` na lista de conexões; a lista de membros da bolha já usava `md`. */
  readonly tamanhoAvatar = input<'md' | 'lg'>('lg');
  /** Repassa o aviso do botão, para a lista tirar a pessoa da aba quando ela muda de situação. */
  readonly mudou = output<SituacaoConexao>();
}
