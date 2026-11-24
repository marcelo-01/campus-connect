import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom, map, tap } from 'rxjs';
import { CadastroPendente, NovoCadastro, SessaoResponse, UsuarioResumo } from './sessao';

const ESPERA_ANTES_DE_REPETIR_MS = 300;

/**
 * Guarda a sessão do usuário.
 *
 * O JWT de acesso fica só em memória, nunca no localStorage, para que um script injetado na página não
 * consiga lê-lo depois. Quem mantém o usuário logado entre recargas é o refresh token, que fica num
 * cookie HttpOnly que o JavaScript não enxerga.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly usuarioAtual = signal<UsuarioResumo | null>(null);
  private tokenAcesso: string | null = null;
  private renovacaoEmAndamento: Promise<boolean> | null = null;

  readonly usuario = this.usuarioAtual.asReadonly();
  readonly autenticado = computed(() => this.usuarioAtual() !== null);

  get accessToken(): string | null {
    return this.tokenAcesso;
  }

  entrar(email: string, senha: string, manterConectado: boolean): Observable<UsuarioResumo> {
    return this.http
      .post<SessaoResponse>('/api/auth/login', { email, senha, manterConectado })
      .pipe(
        tap((sessao) => this.guardar(sessao)),
        map((sessao) => sessao.usuario),
      );
  }

  /** Cria a conta e manda o código para o e-mail. Não abre a sessão: ela abre em {@link verificarEmail}. */
  cadastrar(dados: NovoCadastro): Observable<CadastroPendente> {
    return this.http.post<CadastroPendente>('/api/auth/cadastro', dados);
  }

  /** Confere o código mandado para o e-mail e, se ele bater, já entra na conta. */
  verificarEmail(email: string, codigo: string): Observable<UsuarioResumo> {
    return this.http.post<SessaoResponse>('/api/auth/verificar-email', { email, codigo }).pipe(
      tap((sessao) => this.guardar(sessao)),
      map((sessao) => sessao.usuario),
    );
  }

  /** Pede outro código. O servidor responde 202 até para e-mail sem conta, e 429 se for cedo demais. */
  reenviarCodigo(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/reenviar-codigo', { email });
  }

  /**
   * O "Esqueceu a senha?": manda o código que troca a senha. Como no reenvio, o servidor responde 202 até para
   * e-mail sem conta, e 429 se for cedo demais.
   */
  esqueciSenha(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/esqueci-senha', { email });
  }

  /** Confere o código de redefinição antes da senha nova. O código não é gasto: ele vai de novo em {@link redefinirSenha}. */
  conferirCodigoRedefinicao(email: string, codigo: string): Observable<void> {
    return this.http.post<void>('/api/auth/redefinir-senha/conferir', { email, codigo });
  }

  /** Troca a senha pelo código e já entra na conta. As sessões abertas em outros aparelhos caem. */
  redefinirSenha(email: string, codigo: string, novaSenha: string): Observable<UsuarioResumo> {
    return this.http
      .post<SessaoResponse>('/api/auth/redefinir-senha', { email, codigo, novaSenha })
      .pipe(
        tap((sessao) => this.guardar(sessao)),
        map((sessao) => sessao.usuario),
      );
  }

  /**
   * Troca a senha de quem está logado. O servidor reconhece a pessoa pelo cookie do refresh token, encerra as sessões
   * dos outros aparelhos e devolve uma sessão nova para este. Se o cookie não valer mais (401), a sessão local também
   * acaba.
   */
  alterarSenha(senhaAtual: string, novaSenha: string): Observable<UsuarioResumo> {
    return this.http
      .post<SessaoResponse>('/api/auth/alterar-senha', { senhaAtual, novaSenha })
      .pipe(
        tap({
          next: (sessao) => this.guardar(sessao),
          error: (erro: unknown) => {
            if (erro instanceof HttpErrorResponse && erro.status === 401) {
              this.limpar();
            }
          },
        }),
        map((sessao) => sessao.usuario),
      );
  }

  /**
   * Troca o refresh token (cookie) por um JWT novo. Chamadas simultâneas compartilham a mesma
   * requisição, porque cada refresh token só pode ser usado uma vez.
   */
  renovar(): Promise<boolean> {
    this.renovacaoEmAndamento ??= this.tentarRenovar().finally(() => (this.renovacaoEmAndamento = null));
    return this.renovacaoEmAndamento;
  }

  /** Chamado na abertura do app: se o cookie ainda vale, a pessoa continua logada depois de um F5. */
  async restaurarSessao(): Promise<void> {
    await this.renovar();
  }

  async sair(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', null));
    } catch {
      // Mesmo se o servidor não responder, a sessão local acaba aqui.
    } finally {
      this.limpar();
    }
  }

  /** Depois de editar o perfil: o menu lateral e a barra do topo mostram o nome e a foto novos na hora. */
  atualizarUsuario(campos: Partial<UsuarioResumo>): void {
    this.usuarioAtual.update((atual) => (atual ? { ...atual, ...campos } : atual));
  }

  limpar(): void {
    this.tokenAcesso = null;
    this.usuarioAtual.set(null);
  }

  private async tentarRenovar(): Promise<boolean> {
    for (let tentativa = 1; tentativa <= 2; tentativa++) {
      try {
        const sessao = await firstValueFrom(this.http.post<SessaoResponse>('/api/auth/refresh', null));
        this.guardar(sessao);
        return true;
      } catch (erro) {
        // 409: outra aba acabou de renovar e o navegador já está com o cookie novo. Basta repetir.
        const outraAbaRenovou = erro instanceof HttpErrorResponse && erro.status === 409;
        if (!outraAbaRenovou || tentativa === 2) {
          this.limpar();
          return false;
        }
        await new Promise((pronto) => setTimeout(pronto, ESPERA_ANTES_DE_REPETIR_MS));
      }
    }
    return false;
  }

  private guardar(sessao: SessaoResponse): void {
    this.tokenAcesso = sessao.accessToken;
    this.usuarioAtual.set(sessao.usuario);
  }
}
