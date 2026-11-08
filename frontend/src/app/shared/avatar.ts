import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** "Ana Beatriz Casiano" → "AC": primeira letra do primeiro e do último nome. */
export function iniciais(nome: string | null | undefined): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  const primeira = partes[0]?.[0] ?? '';
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}

/**
 * O `Avatar` do MVP: círculo com as iniciais sobre o gradiente da marca. Com foto, a foto ocupa o círculo.
 * O tamanho usa as classes do MVP (`sm`, `md`, `lg`, `xl`).
 */
@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  template: `
    <div class="avatar" [class]="tamanho()">
      @if (fotoUrl(); as url) {
        <img [src]="url" [alt]="nome()" />
      } @else {
        {{ letras() }}
      }
    </div>
  `,
})
export class Avatar {
  readonly nome = input<string | null | undefined>('');
  readonly fotoUrl = input<string | null | undefined>(null);
  readonly tamanho = input<'sm' | 'md' | 'lg' | 'xl'>('md');

  protected readonly letras = computed(() => iniciais(this.nome()));
}
