package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/**
 * Onde a próxima página da vitrine começa. Vai para o frontend como um texto opaco, que ele devolve como
 * veio. Por dentro é quantos anúncios já foram mostrados ({@code d|<n>}), porque a vitrine pode ser
 * ordenada por preço e por nota, que não servem de chave estável.
 */
final class CursorVitrine {

    private CursorVitrine() {
    }

    /** Quantos anúncios pular; sem cursor, a primeira página. */
    static int ler(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        try {
            String[] partes = new String(Base64.getUrlDecoder().decode(texto.strip()), StandardCharsets.UTF_8)
                    .split("\\|");
            if (partes.length == 2 && partes[0].equals("d")) {
                int deslocamento = Integer.parseInt(partes[1]);
                if (deslocamento >= 0) {
                    return deslocamento;
                }
            }
        } catch (RuntimeException invalido) {
            // Responde abaixo, com a mensagem do campo.
        }
        throw new CampoInvalidoException("cursor", "Não foi possível continuar a lista. Recarregue a página.");
    }

    static String depoisDe(int deslocamento) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("d|" + deslocamento).getBytes(StandardCharsets.UTF_8));
    }
}
