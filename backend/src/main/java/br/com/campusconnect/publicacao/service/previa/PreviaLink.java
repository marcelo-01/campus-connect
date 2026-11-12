package br.com.campusconnect.publicacao.service.previa;

import org.jspecify.annotations.Nullable;

/**
 * O que o servidor conseguiu ler da página de um link.
 *
 * @param imagemUrl cópia da imagem de destaque, já gravada em {@code /api/arquivos}; quem recebe fica
 *     responsável por apagá-la se não usar
 */
public record PreviaLink(@Nullable String titulo, @Nullable String descricao, @Nullable String site,
        @Nullable String imagemUrl) {
}
