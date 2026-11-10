import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BolhaMarca } from '../shared/bolha-marca';
import { Icone } from '../shared/icone';
import { BolhaResumo, membrosPorExtenso } from './bolha.service';

/**
 * O card de bolha do Explorar do MVP: círculo, nome, membros e o "+". Para quem já participa, o "+" vira
 * um check. Leva à página da bolha.
 */
@Component({
  selector: 'app-bolha-cartao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BolhaMarca, Icone],
  styles: ':host { display: contents; }',
  template: `
    <a class="card explore-card" [routerLink]="['/bolhas', bolha().slug]">
      <app-bolha-marca [bolha]="bolha()" />
      <div class="explore-card-info">
        <div class="explore-card-nome">{{ bolha().nome }}</div>
        <div class="explore-card-meta">{{ linha() }}</div>
      </div>
      @if (bolha().membro) {
        <span class="explore-card-membro" title="Você participa"><app-icone nome="check" /></span>
      } @else {
        <app-icone nome="mais" />
      }
    </a>
  `,
})
export class BolhaCartao {
  readonly bolha = input.required<BolhaResumo>();
  /** Nome da bolha acima, mostrado depois dos membros. */
  readonly acima = input<string | null | undefined>(null);

  protected readonly linha = computed(() => {
    const membros = membrosPorExtenso(this.bolha().totalMembros);
    return this.acima() ? `${membros} · ${this.acima()}` : membros;
  });
}
