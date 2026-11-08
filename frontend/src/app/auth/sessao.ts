/** Contratos da API de autenticação (`/api/auth/**` e `/api/usuarios/me`). */

export interface UsuarioResumo {
  id: string;
  username: string;
  nomeCompleto: string;
  nomeExibicao: string | null;
  email: string;
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

/** Corpo de erro da API (ProblemDetail, RFC 9457). `erros` mapeia campo para mensagem. */
export interface ProblemaApi {
  status?: number;
  detail?: string;
  erros?: Record<string, string>;
}
