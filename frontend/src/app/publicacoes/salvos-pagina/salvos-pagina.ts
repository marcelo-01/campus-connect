import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Feed } from '../feed/feed';
import { EscopoFeed } from '../publicacao.service';

/**
 * Os posts salvos. No MVP o item "Salvos" do menu só mostrava um aviso de rascunho. Tirar um post dos
 * salvos aqui o tira da lista na hora.
 */
@Component({
  selector: 'app-salvos-pagina',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Feed],
  styles: ':host { display: contents; }',
  template: `
    <div class="content content-narrow">
      <div>
        <h1 class="section-title">Salvos</h1>
        <div class="section-sub">Só você vê o que salvou.</div>
      </div>
      <app-feed
        [escopo]="escopo"
        [comChips]="false"
        estiloVazio="cartao"
        vazioTexto="Você ainda não salvou nada. Os posts que você salvar aparecerão aqui."
      />
    </div>
  `,
})
export class SalvosPagina {
  protected readonly escopo: EscopoFeed = { tipo: 'salvas' };
}
