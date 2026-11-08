import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ToastService } from '../../shared/toast.service';
import { IconeGoogle, IconeOlho } from '../../shared/icones';
import { aplicarErroDaApi, emailValido, mensagemDeErro } from '../../shared/formulario';
import { AuthLayout } from '../auth-layout/auth-layout';
import { AuthService } from '../auth.service';

const MENSAGENS: Record<string, Record<string, string>> = {
  email: { required: 'Informe seu e-mail.', email: 'Informe um e-mail válido.' },
  senha: { required: 'Informe sua senha.' },
};

/** Tela de login. A marcação é a do `auth.jsx` do MVP, agora ligada à API. */
@Component({
  selector: 'app-entrar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuthLayout, ReactiveFormsModule, RouterLink, IconeOlho, IconeGoogle],
  styles: ':host { display: contents; }',
  templateUrl: './entrar.html',
})
export class Entrar {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly mostrarSenha = signal(false);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, emailValido]],
    senha: ['', Validators.required],
    manterConectado: [true],
  });

  protected entrar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, senha, manterConectado } = this.form.getRawValue();
    this.enviando.set(true);
    this.auth.entrar(email.trim(), senha, manterConectado).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (erro: unknown) => {
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
        this.enviando.set(false);
      },
    });
  }

  protected mostrarErro(campo: 'email' | 'senha'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: 'email' | 'senha'): string | null {
    return mensagemDeErro(this.form.controls[campo], MENSAGENS[campo]);
  }

  protected emBreve(evento: Event, recurso: string): void {
    evento.preventDefault();
    this.toast.mostrar(`${recurso} — em breve`);
  }
}
