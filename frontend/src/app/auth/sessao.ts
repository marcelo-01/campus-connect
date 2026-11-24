/** Contratos da API de autenticação (`/api/auth/**` e `/api/usuarios/me`). */

export interface UsuarioResumo {
  id: string;
  username: string;
  nomeCompleto: string;
  nomeExibicao: string | null;
  email: string;
  /** Caminho da foto de perfil (`/api/arquivos/…`), ou `null` para mostrar as iniciais. */
  fotoUrl: string | null;
  /** Pode criar, editar e excluir bolhas. O backend confere de novo a cada ação. */
  administrador: boolean;
}

/** O refresh token não vem aqui: o servidor o entrega só no cookie HttpOnly `cc_refresh`. */
export interface SessaoResponse {
  accessToken: string;
  expiraEm: string;
  usuario: UsuarioResumo;
}

export interface NovoCadastro {
  nomeCompleto: string;
  username: string;
  email: string;
  senha: string;
}

/** Resposta do cadastro: a conta existe, mas a sessão só abre depois do código mandado para o e-mail. */
export interface CadastroPendente {
  email: string;
}

/** O `codigo` do 403 do login quando a senha está certa, mas o e-mail ainda não foi verificado. */
export const EMAIL_NAO_VERIFICADO = 'email-nao-verificado';

/** Corpo de erro da API (ProblemDetail, RFC 9457). `erros` mapeia campo para mensagem. */
export interface ProblemaApi {
  status?: number;
  detail?: string;
  erros?: Record<string, string>;
  /** Identifica um erro que a tela trata de um jeito próprio, como {@link EMAIL_NAO_VERIFICADO}. */
  codigo?: string;
  /** No 429 do reenvio de código: quanto falta para poder pedir outro. */
  aguardarSegundos?: number;
}
