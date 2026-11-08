import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UsuarioResumo } from '../auth/sessao';

/**
 * Página provisória depois do login, até o feed do MVP chegar.
 *
 * Busca os dados em `GET /api/usuarios/me` em vez de usar os do login, para provar que o JWT abre as
 * rotas protegidas.
 */
@Component({
  selector: 'app-inicio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: ':host { display: contents; }',
  templateUrl: './inicio.html',
})
export class Inicio {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly usuario = signal<UsuarioResumo | null>(this.auth.usuario());
  protected readonly saindo = signal(false);

  protected readonly primeiroNome = computed(() => {
    const usuario = this.usuario();
    return (usuario?.nomeExibicao || usuario?.nomeCompleto || '').split(/\s+/)[0];
  });

  protected readonly iniciais = computed(() => {
    const partes = (this.usuario()?.nomeCompleto ?? '').trim().split(/\s+/).filter(Boolean);
    const primeira = partes[0]?.[0] ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primeira + ultima).toUpperCase();
  });

  constructor() {
    inject(HttpClient)
      .get<UsuarioResumo>('/api/usuarios/me')
      .subscribe({ next: (usuario) => this.usuario.set(usuario), error: () => undefined });
  }

  protected async sair(): Promise<void> {
    this.saindo.set(true);
    await this.auth.sair();
    await this.router.navigateByUrl('/entrar');
  }
}
