package br.com.campusconnect.conversa.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Mensagens da mais nova para a mais antiga.
 *
 * @param proximo onde começa a página das mensagens anteriores; {@code null} se não há mais
 */
public record PaginaMensagens(List<MensagemVista> itens, @Nullable String proximo) {
}
