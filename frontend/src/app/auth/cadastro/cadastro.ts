import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ToastService } from '../../shared/toast.service';
import { IconeGoogle, IconeOlho } from '../../shared/icones';
import {
  aplicarErroDaApi,
  emailValido,
  igualASenha,
  mensagemDeErro,
  normalizarUsername,
  usernameValido,
} from '../../shared/formulario';
import { AuthLayout } from '../auth-layout/auth-layout';
import { AuthService } from '../auth.service';

type Campo = 'nomeCompleto' | 'username' | 'email' | 'senha' | 'confirmarSenha';

const TAMANHO_SENHA = 'A senha precisa ter de 8 a 128 caracteres.';

/** Mesmas mensagens do `CadastroRequest` no backend. */
const MENSAGENS: Record<Campo, Record<string, string>> = {
  nomeCompleto: { required: 'Informe seu nome completo.', maxlength: 'Use no máximo 150 caracteres.' },
  username: {
    required: 'Escolha um nome de usuário.',
    username: 'Use de 3 a 30 caracteres: letras, números, ponto ou sublinhado.',
  },
  email: {
    required: 'Informe seu e-mail.',
    email: 'Informe um e-mail válido.',
    maxlength: 'Use no máximo 150 caracteres.',
  },
  senha: { required: 'Crie uma senha.', minlength: TAMANHO_SENHA, maxlength: TAMANHO_SENHA },
  confirmarSenha: { required: 'Repita a senha.', diferente: 'As senhas não são iguais.' },
};

/**
 * Tela de cadastro. O MVP não tinha uma; esta usa a mesma moldura e os mesmos componentes visuais do login.
 * A conta já sai logada.
 */
@Component({
  selector: 'app-cadastro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuthLayout, ReactiveFormsModule, RouterLink, IconeOlho, IconeGoogle],
  styles: ':host { display: contents; }',
  templateUrl: './cadastro.html',
})
export class Cadastro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly mostrarSenha = signal(false);
  protected readonly mostrarConfirmacao = signal(false);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nomeCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    username: ['', [Validators.required, usernameValido]],
    email: ['', [Validators.required, emailValido, Validators.maxLength(150)]],
    senha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirmarSenha: ['', [Validators.required, igualASenha]],
  });

  constructor() {
    // Mudar a senha pode fazer a confirmação passar a bater (ou deixar de bater).
    this.form.controls.senha.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.form.controls.confirmarSenha.updateValueAndValidity({ emitEvent: false }));
  }

  protected cadastrar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.form.invalid || this.enviando()) {
      this.form.markAllAsTouched();
      return;
    }

    const { nomeCompleto, username, email, senha } = this.form.getRawValue();
    this.enviando.set(true);
    this.auth
      .cadastrar({
        nomeCompleto: nomeCompleto.trim(),
        username: normalizarUsername(username),
        email: email.trim(),
        senha,
      })
      .subscribe({
        next: () => void this.router.navigateByUrl('/'),
        error: (erro: unknown) => {
          this.erroGeral.set(aplicarErroDaApi(this.form, erro));
          this.enviando.set(false);
        },
      });
  }

  protected mostrarErro(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: Campo): string | null {
    return mensagemDeErro(this.form.controls[campo], MENSAGENS[campo]);
  }

  protected emBreve(evento: Event, recurso: string): void {
    evento.preventDefault();
    this.toast.mostrar(`${recurso} — em breve`);
  }
}
