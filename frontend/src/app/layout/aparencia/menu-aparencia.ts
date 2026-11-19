import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Icone } from '../../shared/icone';
import { AparenciaService, CORES, TEMAS } from './aparencia.service';

/**
 * O botão de aparência da barra do topo e o painel que ele abre: o tema e a cor de destaque, como na tela
 * "Exibição" do X. A escolha vale na hora, sem botão de salvar.
 *
 * As opções são rádios de verdade, escondidos atrás do desenho, para as setas do teclado trocarem a opção.
 * Cada tema mostra uma prévia do próprio fundo, com as cores fixas no CSS: ela não muda com o tema da tela.
 * Cada amostra de cor recebe o próprio `data-accent`, então o tom vem do mesmo CSS que pinta o resto da tela.
 */
@Component({
  selector: 'app-menu-aparencia',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  styles: ':host { display: contents; }',
  host: {
    '(document:click)': 'fecharSeForFora($event)',
    '(document:keydown.escape)': 'fecharPeloTeclado()',
  },
  template: `
    <div class="menu-ancora">
      <button
        #botao
        type="button"
        class="icon-btn"
        title="Aparência"
        aria-label="Aparência"
        aria-haspopup="dialog"
        [attr.aria-expanded]="aberto()"
        (click)="aberto.set(!aberto())"
      >
        <app-icone nome="aparencia" />
      </button>
      @if (aberto()) {
        <div class="menu-flutuante aparencia-painel" role="dialog" aria-label="Aparência">
          <fieldset class="aparencia-grupo">
            <legend>Tema</legend>
            <div class="aparencia-temas">
              @for (tema of temas; track tema.valor) {
                <label class="aparencia-tema" [attr.data-tema]="tema.valor">
                  <input
                    type="radio"
                    name="aparencia-tema"
                    class="visualmente-oculto"
                    [value]="tema.valor"
                    [checked]="aparencia.tema() === tema.valor"
                    (change)="aparencia.escolherTema(tema.valor)"
                  />
                  <span class="aparencia-previa" aria-hidden="true">
                    @if (tema.valor === 'automatico') {
                      <span class="metade-clara">Aa</span><span class="metade-escura">Aa</span>
                    } @else {
                      <span>Aa</span>
                    }
                  </span>
                  {{ tema.rotulo }}
                </label>
              }
            </div>
            @if (aparencia.tema() === 'automatico') {
              <p class="aparencia-dica">Segue o tema do seu aparelho.</p>
            }
          </fieldset>

          <fieldset class="aparencia-grupo">
            <legend>Cor de destaque</legend>
            <div class="aparencia-cores">
              @for (cor of cores; track cor.valor) {
                <label class="aparencia-cor" [attr.data-accent]="cor.valor" [title]="cor.rotulo">
                  <input
                    type="radio"
                    name="aparencia-cor"
                    class="visualmente-oculto"
                    [value]="cor.valor"
                    [checked]="aparencia.cor() === cor.valor"
                    [attr.aria-label]="cor.rotulo"
                    (change)="aparencia.escolherCor(cor.valor)"
                  />
                  @if (aparencia.cor() === cor.valor) {
                    <app-icone nome="check" />
                  }
                </label>
              }
            </div>
          </fieldset>

          <p class="aparencia-dica">Vale para todas as contas neste navegador.</p>
        </div>
      }
    </div>
  `,
})
export class MenuAparencia {
  protected readonly aparencia = inject(AparenciaService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly botao = viewChild.required<ElementRef<HTMLButtonElement>>('botao');

  protected readonly temas = TEMAS;
  protected readonly cores = CORES;
  protected readonly aberto = signal(false);

  protected fecharSeForFora(evento: Event): void {
    if (this.aberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.aberto.set(false);
    }
  }

  /** O foco volta para o botão, senão ele se perderia junto com o painel. */
  protected fecharPeloTeclado(): void {
    if (this.aberto()) {
      this.aberto.set(false);
      this.botao().nativeElement.focus();
    }
  }
}
