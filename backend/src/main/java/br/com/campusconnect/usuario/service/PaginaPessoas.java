package br.com.campusconnect.usuario.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uma página da busca de pessoas.
 *
 * @param proximo cursor da página seguinte, para mandar de volta como está; {@code null} na última
 */
public record PaginaPessoas(List<UsuarioPublico> itens, @Nullable String proximo) {
}
