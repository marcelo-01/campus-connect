import { ChangeDetectionStrategy, Component, computed, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { BolhaService, corDaBolha, siglaExibida } from '../bolhas/bolha.service';
import { Composer } from '../publicacoes/composer/composer';
import { Feed } from '../publicacoes/feed/feed';
import { EscopoFeed, Publicacao } from '../publicacoes/publicacao.service';

/**
 * O `FeedScreen` do MVP: o composer, os chips e os posts das bolhas da pessoa. Como entrar numa bolha
 * também entra nas de cima, o que é publicado na bolha geral chega aqui.
 *
 * Antes dos chips fica o filtro por bolha: escolhida uma, o feed mostra só os posts feitos nela, sem os
 * que descem das bolhas de cima. A escolha fica na URL (`/?bolha=<slug>`) e vira o destino do composer.
 */
@Component({
  selector: 'app-inicio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Composer, Feed],
  styles: ':host { display: contents; }',
  templateUrl: './inicio.html',
})
export class Inicio {
  private readonly bolhas = inject(BolhaService);
  private readonly router = inject(Router);

  private readonly feed = viewChild(Feed);
  private readonly slugDoFiltro = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((parametros) => parametros.get('bolha'))),
    { initialValue: null },
  );

  /** `null` enquanto o menu lateral ainda carrega as bolhas. */
  protected readonly minhas = this.bolhas.minhas;
  /** Bolha do filtro; uma que a pessoa deixou fica de fora, e o feed volta a mostrar todas. */
  protected readonly filtro = computed(() => {
    const slug = this.slugDoFiltro();
    return slug ? (this.minhas()?.find((b) => b.slug === slug) ?? null) : null;
  });
  protected readonly escopo = computed<EscopoFeed>(() => ({
    tipo: 'inicio',
    bolhaId: this.filtro()?.id ?? null,
  }));
  protected readonly vazio = computed(() => {
    const bolha = this.filtro();
    return bolha
      ? {
          titulo: `Nada por aqui ainda em ${bolha.nome}`,
          texto: 'Seja a primeira pessoa a publicar nesta bolha — ou ajuste o filtro acima.',
          sigla: siglaExibida(bolha, 2),
          cor: corDaBolha(bolha.tipo),
        }
      : {
          titulo: 'Nada nas suas bolhas ainda',
          texto: 'Seja a primeira pessoa a publicar: escreva algo aí em cima.',
          sigla: 'CC',
          cor: 'var(--accent)',
        };
  });

  /** Recuo das sub-bolhas no seletor, como no menu lateral. Espaço comum some dentro de `<option>`. */
  protected readonly recuo = (nivel: number) => ' '.repeat(nivel * 3);

  protected filtrar(slug: string): void {
    void this.router.navigate([], { queryParams: { bolha: slug || null } });
  }

  /** O post novo aparece na hora, se for da bolha do filtro (ou se não houver filtro). */
  protected aoPublicar(publicacao: Publicacao): void {
    const filtro = this.filtro();
    if (!filtro || filtro.id === publicacao.bolha.id) {
      this.feed()?.adicionar(publicacao);
    }
  }
}
