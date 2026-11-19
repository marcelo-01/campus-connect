package br.com.campusconnect.notificacao.service;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Uma página de notificações, da mais nova para a mais antiga.
 *
 * @param proximo onde a próxima página começa; {@code null} na última
 * @param maisNovaEm só na primeira página: quando chegou a notificação mais nova. A tela marca como lidas as que
 *     chegaram até aí, e não as que chegarem enquanto ela carrega.
 */
public record PaginaNotificacoes(List<NotificacaoVista> itens, @Nullable String proximo,
        @Nullable Instant maisNovaEm) {
}
