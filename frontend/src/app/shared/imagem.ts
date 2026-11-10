/** Limites conferidos de novo pelo backend, para foto de perfil, capa e foto de bolha. */
export const TAMANHO_MAXIMO_IMAGEM = 5 * 1024 * 1024;
export const TIPOS_DE_IMAGEM = ['image/jpeg', 'image/png'];

/** Mensagem de erro para mostrar antes de enviar, ou `null` se o arquivo pode seguir. */
export function problemaNaImagem(arquivo: File): string | null {
  if (!TIPOS_DE_IMAGEM.includes(arquivo.type)) {
    return 'Escolha uma imagem JPG ou PNG.';
  }
  if (arquivo.size > TAMANHO_MAXIMO_IMAGEM) {
    return 'A imagem pode ter no máximo 5 MB.';
  }
  return null;
}

/** Corpo multipart com o arquivo na parte `arquivo`, como a API espera. */
export function comArquivo(arquivo: File): FormData {
  const formulario = new FormData();
  formulario.append('arquivo', arquivo);
  return formulario;
}
