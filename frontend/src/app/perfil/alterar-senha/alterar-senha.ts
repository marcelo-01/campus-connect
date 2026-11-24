import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { aplicarErroDaApi, igualAoCampo, mensagemDeErro } from '../../shared/formulario';
import { Icone } from '../../shared/icone';
import { IconeOlho } from '../../shared/icones';
import { ToastService } from '../../shared/toast.service';

type Campo = 'senhaAtual' | 'novaSenha' | 'confirmarSenha';

const TAMANHO_SENHA = 'A senha precisa ter de 8 a 128 caracteres.';

/** Mesmas mensagens do `AlteracaoSenhaRequest` no backend. */
const MENSAGENS: Record<Campo, Record<string, string>> = {
  senhaAtual: { required: 'Informe sua senha atual.' },
  novaSenha: {
    required: 'Crie uma senha nova.',
    minlength: TAMANHO_SENHA,
    maxlength: TAMANHO_SENHA,
  },
  confirmarSenha: { required: 'Repita a senha nova.', diferente: 'As senhas não são iguais.' },
};

/**
 * A troca de senha de quem está logado, aberta pelo fim de "Editar perfil". Pede a senha atual. Depois de salvar, a
 * conta sai dos outros aparelhos, e este continua logado com uma sessão nova.
 */
@Component({
  selector: 'app-alterar-senha',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, Icone, IconeOlho],
  styles: ':host { display: contents; }',
  templateUrl: './alterar-senha.html',
})
export class AlterarSenha {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Para o gerenciador de senhas saber de que conta é a senha nova. */
  protected readonly email = this.auth.usuario()?.email ?? '';
  protected readonly salvando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  protected readonly mostrar = signal<Record<Campo, boolean>>({
    senhaAtual: false,
    novaSenha: false,
    confirmarSenha: false,
  });

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    senhaAtual: ['', Validators.required],
    novaSenha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirmarSenha: ['', [Validators.required, igualAoCampo('novaSenha')]],
  });

  constructor() {
    // Mudar a senha pode fazer a confirmação passar a bater (ou deixar de bater).
    this.form.controls.novaSenha.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() =>
        this.form.controls.confirmarSenha.updateValueAndValidity({ emitEvent: false }),
      );

    afterNextRender(() => this.campo().nativeElement.focus());
  }

  protected salvar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.form.invalid || this.salvando()) {
      this.form.markAllAsTouched();
      return;
    }

    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.salvando.set(true);
    this.auth.alterarSenha(senhaAtual, novaSenha).subscribe({
      next: () => {
        this.toast.mostrar('Senha alterada. A conta saiu dos outros aparelhos.');
        void this.router.navigateByUrl('/perfil/editar');
      },
      error: (erro: unknown) => {
        this.salvando.set(false);
        if (erro instanceof HttpErrorResponse && erro.status === 401) {
          // A sessão deste aparelho já tinha acabado, e o AuthService já a esqueceu.
          this.toast.mostrar('Sua sessão expirou. Entre novamente.');
          void this.router.navigateByUrl('/entrar');
          return;
        }
        this.erroGeral.set(aplicarErroDaApi(this.form, erro));
      },
    });
  }

  protected voltar(): void {
    void this.router.navigateByUrl('/perfil/editar');
  }

  protected alternar(campo: Campo): void {
    this.mostrar.update((atual) => ({ ...atual, [campo]: !atual[campo] }));
  }

  protected mostrarErro(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: Campo): string | null {
    return mensagemDeErro(this.form.controls[campo], MENSAGENS[campo]);
  }
}
