package br.com.campusconnect.conversa.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uma conversa na lista.
 *
 * @param pessoa a outra pessoa da conversa
 * @param naoLida se há mensagem da outra pessoa que quem vê ainda não leu
 */
public record ConversaResumo(UUID id, UsuarioPublico pessoa, Previa ultima, boolean naoLida) {

    /**
     * A última mensagem da conversa.
     *
     * @param texto o começo do texto, numa linha só; {@code null} se a mensagem foi apagada
     * @param minha se quem vê mandou a mensagem
     */
    public record Previa(UUID mensagemId, @Nullable String texto, boolean minha, Instant enviadaEm, boolean apagada) {
    }
}
