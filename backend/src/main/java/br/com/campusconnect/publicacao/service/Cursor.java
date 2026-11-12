package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Onde a próxima página do feed começa. Vai para o frontend como um texto opaco, que ele devolve como
 * veio. Por dentro é a data e o id do último item ({@code k|<data>|<id>}) ou, no "Em alta", quantos itens
 * já foram mostrados ({@code d|<n>}).
 */
record Cursor(@Nullable Instant em, @Nullable UUID id, int deslocamento) {

    static final Cursor INICIO = new Cursor(null, null, 0);

    static Cursor ler(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return INICIO;
        }
        try {
            String[] partes = new String(Base64.getUrlDecoder().decode(texto.strip()), StandardCharsets.UTF_8)
                    .split("\\|");
            if (partes.length == 3 && partes[0].equals("k")) {
                return new Cursor(Instant.parse(partes[1]), UUID.fromString(partes[2]), 0);
            }
            if (partes.length == 2 && partes[0].equals("d")) {
                int deslocamento = Integer.parseInt(partes[1]);
                if (deslocamento >= 0) {
                    return new Cursor(null, null, deslocamento);
                }
            }
        } catch (RuntimeException invalido) {
            // Cai na mensagem abaixo.
        }
        throw new CampoInvalidoException("cursor", "Não foi possível continuar o feed. Recarregue a página.");
    }

    static String depoisDe(Instant em, UUID id) {
        return codificar("k|" + em + "|" + id);
    }

    static String deslocado(int deslocamento) {
        return codificar("d|" + deslocamento);
    }

    private static String codificar(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
