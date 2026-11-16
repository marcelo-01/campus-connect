import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Avatar } from '../../shared/avatar';
import { Icone } from '../../shared/icone';
import { ToastService } from '../../shared/toast.service';
import { AnuncioCard, AnuncioService } from '../anuncio.service';
import { gradienteDaCapa, notaPorExtenso, precoPorExtenso, textoDaCapa } from '../preco';

/**
 * O card de serviço do `MarketScreen` do MVP com dados reais: a capa (a primeira foto ou, sem foto, o começo
 * do título sobre um gradiente), o coração de favorito, quem anunciou, a bolha, o preço e a nota. O selo
 * "impulsionado" saiu: o impulsionamento vem numa etapa própria.
 *
 * O título é o link do anúncio e cobre o card inteiro; o coração fica por cima, para favoritar sem abrir o
 * anúncio. Na prévia do formulário, o card não tem link nem coração.
 */
@Component({
  selector: 'app-anuncio-cartao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './anuncio-cartao.html',
})
export class AnuncioCartao {
  private readonly servico = inject(AnuncioService);
  private readonly toast = inject(ToastService);

  readonly anuncio = input.required<AnuncioCard>();
  /** A pré-visualização do formulário: sem link, sem coração e com o gradiente tirado do título. */
  readonly previa = input(false);

  readonly mudou = output<AnuncioCard>();

  protected readonly estado = linkedSignal(() => this.anuncio());
  protected readonly preco = computed(() => precoPorExtenso(this.estado().modalidadePreco, this.estado().preco));
  protected readonly capa = computed(() => {
    const anuncio = this.estado();
    return {
      fundo: gradienteDaCapa(this.previa() ? anuncio.titulo : anuncio.id),
      texto: textoDaCapa(anuncio.titulo),
    };
  });
  /** A bolha mais ampla do anúncio, com "+N" quando há mais. */
  protected readonly bolha = computed(() => {
    const bolhas = this.estado().bolhas;
    if (!bolhas.length) {
      return null;
    }
    return bolhas.length > 1 ? `${bolhas[0].nome} +${bolhas.length - 1}` : bolhas[0].nome;
  });
  protected readonly nota = notaPorExtenso;

  /** Fica dentro do card, que é um link: sem o `preventDefault`, o clique também abriria o anúncio. */
  protected favoritar(evento: Event): void {
    evento.preventDefault();
    evento.stopPropagation();
    const antes = this.estado();
    const ligar = !antes.favorito;
    this.aplicar({ favorito: ligar });
    this.toast.mostrar(ligar ? 'Salvo nos favoritos' : 'Removido dos favoritos');
    this.servico.favoritar(antes.id, ligar).subscribe({
      error: () => {
        this.aplicar({ favorito: antes.favorito });
        this.toast.mostrar('Não foi possível concluir. Tente de novo.');
      },
    });
  }

  private aplicar(mudanca: Partial<AnuncioCard>): void {
    this.estado.update((atual) => ({ ...atual, ...mudanca }));
    this.mudou.emit(this.estado());
  }
}
