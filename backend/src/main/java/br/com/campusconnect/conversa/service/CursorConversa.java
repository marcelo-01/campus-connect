package br.com.campusconnect.conversa.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Onde a próxima página começa, nas listas de conversas e de mensagens. Vai para o frontend como um texto opaco, que
 * ele devolve como veio. Por dentro é a data e o id do último item ({@code k|<data>|<id>}), como no feed.
 */
record CursorConversa(Instant em, UUID id) {

    /** {@code null} na primeira página. */
    static @Nullable CursorConversa ler(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            String[] partes = new String(Base64.getUrlDecoder().decode(texto.strip()), StandardCharsets.UTF_8)
                    .split("\\|");
            if (partes.length == 3 && partes[0].equals("k")) {
                return new CursorConversa(Instant.parse(partes[1]), UUID.fromString(partes[2]));
            }
        } catch (RuntimeException invalido) {
            // cai na mensagem abaixo
        }
        throw new CampoInvalidoException("cursor", "Não foi possível continuar a lista. Recarregue a página.");
    }

    static String depoisDe(Instant em, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("k|" + em + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
}
