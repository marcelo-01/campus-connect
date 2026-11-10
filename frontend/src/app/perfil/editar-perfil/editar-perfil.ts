import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, WritableSignal, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { ProblemaApi } from '../../auth/sessao';
import { Avatar } from '../../shared/avatar';
import { aplicarErroDaApi, mensagemDeErro, normalizarUsername, usernameValido } from '../../shared/formulario';
import { Icone } from '../../shared/icone';
import { problemaNaImagem } from '../../shared/imagem';
import { ToastService } from '../../shared/toast.service';
import { Perfil, PerfilService, linkSemEsquema } from '../perfil.service';

type Campo = 'nomeCompleto' | 'username' | 'link' | 'bio';

const TAMANHO_BIO = 200;
const MAXIMO_HABILIDADES = 15;
const TAMANHO_HABILIDADE = 30;

const MENSAGENS: Record<Campo, Record<string, string>> = {
  nomeCompleto: { required: 'Informe seu nome completo.', maxlength: 'Use no máximo 150 caracteres.' },
  username: {
    required: 'Escolha um nome de usuário.',
    username: 'Use de 3 a 30 caracteres: letras, números, ponto ou sublinhado.',
  },
  link: { maxlength: 'O link pode ter até 200 caracteres.' },
  bio: { maxlength: `A bio pode ter até ${TAMANHO_BIO} caracteres.` },
};

/**
 * O `EditProfileScreen` do MVP. Ficaram de fora "Curso" e "Campus": não há cadastro oficial de
 * instituições, e se voltarem virão das bolhas. Foto e capa são enviadas assim que escolhidas, como o MVP sugeria com os botões
 * "Trocar foto" e "Trocar capa"; os outros campos só valem ao clicar em "Salvar alterações".
 */
@Component({
  selector: 'app-editar-perfil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, Avatar, Icone],
  styles: ':host { display: contents; }',
  templateUrl: './editar-perfil.html',
})
export class EditarPerfil {
  private readonly auth = inject(AuthService);
  private readonly perfis = inject(PerfilService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly tamanhoBio = TAMANHO_BIO;
  protected readonly tamanhoHabilidade = TAMANHO_HABILIDADE;
  protected readonly maximoHabilidades = MAXIMO_HABILIDADES;

  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly habilidades = signal<string[]>([]);
  protected readonly rascunhoHabilidade = signal('');
  protected readonly salvando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly enviandoFoto = signal(false);
  protected readonly enviandoCapa = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  protected readonly erroImagem = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nomeCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    username: ['', [Validators.required, usernameValido]],
    link: ['', Validators.maxLength(200)],
    bio: ['', Validators.maxLength(TAMANHO_BIO)],
  });

  constructor() {
    this.perfis.buscar(this.auth.usuario()?.username ?? '').subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        this.habilidades.set(perfil.habilidades);
        this.form.setValue({
          nomeCompleto: perfil.nomeCompleto,
          username: perfil.username,
          link: perfil.link ? linkSemEsquema(perfil.link) : '',
          bio: perfil.bio ?? '',
        });
      },
      error: () => this.erroGeral.set('Não foi possível carregar seu perfil. Recarregue a página.'),
    });
  }

  // ---------------------------------------------------------------- habilidades

  /**
   * Lê e limpa o campo direto no elemento, na mesma hora. Se dependesse só do binding, a limpeza ficaria
   * para o próximo ciclo de renderização, e quem já começou a digitar a próxima habilidade teria o texto
   * antigo colado no novo.
   */
  protected adicionarHabilidade(evento?: Event): void {
    evento?.preventDefault();
    const campo = evento?.target instanceof HTMLInputElement ? evento.target : null;
    const nome = (campo?.value ?? this.rascunhoHabilidade()).trim();
    const atuais = this.habilidades();
    const repetida = atuais.some((h) => h.toLowerCase() === nome.toLowerCase());
    if (nome && !repetida && atuais.length < MAXIMO_HABILIDADES) {
      this.habilidades.set([...atuais, nome]);
    }
    if (campo) {
      campo.value = '';
    }
    this.rascunhoHabilidade.set('');
  }

  protected removerHabilidade(nome: string): void {
    this.habilidades.update((atuais) => atuais.filter((h) => h !== nome));
  }

  // ------------------------------------------------------------------- imagens

  protected trocarFoto(evento: Event): void {
    this.enviarImagem(evento, this.enviandoFoto, (arquivo) => this.perfis.trocarFoto(arquivo), 'Foto de perfil atualizada');
  }

  protected trocarCapa(evento: Event): void {
    this.enviarImagem(evento, this.enviandoCapa, (arquivo) => this.perfis.trocarCapa(arquivo), 'Capa atualizada');
  }

  private enviarImagem(
    evento: Event,
    enviando: WritableSignal<boolean>,
    enviar: (arquivo: File) => Observable<Perfil>,
    sucesso: string,
  ): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = ''; // permite escolher o mesmo arquivo de novo
    if (!arquivo) {
      return;
    }
    const problema = problemaNaImagem(arquivo);
    this.erroImagem.set(problema);
    if (problema) {
      return;
    }

    enviando.set(true);
    enviar(arquivo).subscribe({
      next: (perfil) => {
        this.perfil.update((atual) => (atual ? { ...atual, fotoUrl: perfil.fotoUrl, capaUrl: perfil.capaUrl } : perfil));
        this.auth.atualizarUsuario({ fotoUrl: perfil.fotoUrl });
        this.toast.mostrar(sucesso);
        enviando.set(false);
      },
      error: (erro: unknown) => {
        const problemaApi = erro instanceof HttpErrorResponse ? (erro.error as ProblemaApi | null) : null;
        this.erroImagem.set(problemaApi?.erros?.['arquivo'] ?? 'Não foi possível enviar a imagem. Tente de novo.');
        enviando.set(false);
      },
    });
  }

  // -------------------------------------------------------------------- salvar

  protected salvar(): void {
    this.enviado.set(true);
    this.erroGeral.set(null);
    if (this.form.invalid || this.salvando()) {
      this.form.markAllAsTouched();
      return;
    }
    this.adicionarHabilidade(); // o que ficou digitado e não virou etiqueta também vale

    const { nomeCompleto, username, link, bio } = this.form.getRawValue();
    this.salvando.set(true);
    this.perfis
      .atualizar({
        nomeCompleto: nomeCompleto.trim(),
        username: normalizarUsername(username),
        link: link.trim(),
        bio: bio.trim(),
        habilidades: this.habilidades(),
      })
      .subscribe({
        next: (perfil) => {
          this.auth.atualizarUsuario({ username: perfil.username, nomeCompleto: perfil.nomeCompleto });
          this.toast.mostrar('Perfil atualizado');
          void this.router.navigateByUrl('/perfil');
        },
        error: (erro: unknown) => {
          this.erroGeral.set(aplicarErroDaApi(this.form, erro));
          this.salvando.set(false);
        },
      });
  }

  protected cancelar(): void {
    void this.router.navigateByUrl('/perfil');
  }

  protected mostrarErro(campo: Campo): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && (controle.touched || this.enviado());
  }

  protected mensagem(campo: Campo): string | null {
    return mensagemDeErro(this.form.controls[campo], MENSAGENS[campo]);
  }
}
