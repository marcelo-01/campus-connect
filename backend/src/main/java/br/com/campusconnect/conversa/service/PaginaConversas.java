package br.com.campusconnect.conversa.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** @param proximo onde a próxima página começa; {@code null} na última */
public record PaginaConversas(List<ConversaResumo> itens, @Nullable String proximo) {
}
