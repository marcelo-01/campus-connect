package br.com.campusconnect.notificacao.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/**
 * Onde a próxima página começa. Vai para o frontend como um texto opaco, que ele devolve como veio. Por dentro é a
 * data e a chave do último grupo da página ({@code <data>|<chave>}).
 */
record CursorNotificacoes(@Nullable Instant em, @Nullable String chave) {

    static final CursorNotificacoes INICIO = new CursorNotificacoes(null, null);

    boolean inicio() {
        return em == null;
    }

    static CursorNotificacoes ler(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return INICIO;
        }
        try {
            String[] partes = new String(Base64.getUrlDecoder().decode(texto.strip()), StandardCharsets.UTF_8)
                    .split("\\|", 2);
            if (partes.length == 2 && !partes[1].isEmpty()) {
                return new CursorNotificacoes(Instant.parse(partes[0]), partes[1]);
            }
        } catch (RuntimeException invalido) {
            // Cai na mensagem abaixo.
        }
        throw new CampoInvalidoException("cursor", "Não foi possível continuar as notificações. Recarregue a página.");
    }

    static String depoisDe(Instant em, String chave) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((em + "|" + chave).getBytes(StandardCharsets.UTF_8));
    }
}
