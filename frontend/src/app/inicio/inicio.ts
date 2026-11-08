import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** Início provisório, dentro do layout, até o feed do MVP chegar. */
@Component({
  selector: 'app-inicio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styles: ':host { display: contents; }',
  templateUrl: './inicio.html',
})
export class Inicio {
  private readonly auth = inject(AuthService);

  protected readonly primeiroNome = computed(() => {
    const usuario = this.auth.usuario();
    return (usuario?.nomeExibicao || usuario?.nomeCompleto || '').split(/\s+/)[0];
  });
}
