import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ProblemaApi } from '../auth/sessao';

/** Mesmo critério de `Usuario.normalizarUsername` no backend: sem espaços nas pontas, sem `@` e em minúsculas. */
export function normalizarUsername(valor: string): string {
  return valor.trim().replace(/^@/, '').toLowerCase();
}

/** Mesmas regras de `CadastroRequest` no backend. */
export const usernameValido: ValidatorFn = (controle) => {
  const valor = normalizarUsername(controle.value ?? '');
  return valor === '' || /^[a-z0-9._]{3,30}$/.test(valor) ? null : { username: true };
};

export const emailValido: ValidatorFn = (controle) => {
  const valor = (controle.value ?? '').trim();
  return valor === '' || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor) ? null : { email: true };
};

/** Para o "Confirmar senha": compara com o controle `senha` do mesmo grupo. */
export const igualASenha: ValidatorFn = (controle) => {
  const senha = controle.parent?.get('senha')?.value;
  return !controle.value || controle.value === senha ? null : { diferente: true };
};

/** Primeira mensagem de erro do controle. O erro `servidor` traz o texto pronto vindo da API. */
export function mensagemDeErro(
  controle: AbstractControl | null,
  mensagens: Record<string, string>,
): string | null {
  const erros: ValidationErrors | null | undefined = controle?.errors;
  if (!erros) {
    return null;
  }
  if (typeof erros['servidor'] === 'string') {
    return erros['servidor'];
  }
  const chave = Object.keys(erros).find((nome) => nome in mensagens);
  return chave ? mensagens[chave] : null;
}

/**
 * Coloca os erros de campo da API (`erros` do ProblemDetail) nos controles e devolve a mensagem geral para
 * o alerta do formulário, ou `null` quando todos os erros couberam em algum campo.
 */
export function aplicarErroDaApi(form: FormGroup, erro: unknown): string | null {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Algo deu errado. Tente novamente.';
  }
  if (erro.status === 0) {
    return 'Não foi possível falar com o servidor. Confira sua conexão e tente de novo.';
  }

  const problema = (erro.error ?? {}) as ProblemaApi;
  let sobrou = false;
  for (const [campo, mensagem] of Object.entries(problema.erros ?? {})) {
    const controle = form.get(campo);
    if (controle) {
      controle.setErrors({ servidor: mensagem });
      controle.markAsTouched();
    } else {
      sobrou = true;
    }
  }

  if (problema.erros && !sobrou) {
    return null;
  }
  if (erro.status >= 500) {
    return 'O servidor teve um problema. Tente novamente em instantes.';
  }
  return problema.detail ?? 'Algo deu errado. Tente novamente.';
}
