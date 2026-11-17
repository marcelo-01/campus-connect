package br.com.campusconnect.conversa.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A conversa aberta, para quem a vê.
 *
 * @param pessoa a outra pessoa da conversa
 * @param podeEnviar {@code false} se há bloqueio entre as duas pessoas, em qualquer sentido
 * @param bloqueei se quem vê bloqueou a outra pessoa; o aviso no lugar do campo de envio muda com isso
 * @param outroLeuEm até quando a outra pessoa leu, para o "Visto"; {@code null} se ela nunca abriu a conversa
 */
public record ConversaVista(UUID id, UsuarioPublico pessoa, boolean podeEnviar, boolean bloqueei,
        @Nullable Instant outroLeuEm) {
}
