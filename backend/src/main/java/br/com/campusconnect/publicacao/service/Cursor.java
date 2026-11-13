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
 *
 * <p>A marca do topo, com que o frontend pergunta se chegaram posts novos, tem o mesmo formato, com a
 * data e o id do primeiro item.
 */
record Cursor(@Nullable Instant em, @Nullable UUID id, int deslocamento) {

    static final Cursor INICIO = new Cursor(null, null, 0);

    /** A marca de um feed que abriu vazio: qualquer post que aparecer depois é novo. */
    static final String MARCA_FEED_VAZIO = marca(Instant.EPOCH, new UUID(0, 0));

    /** A marca do topo já lida: o post mais novo que o feed mostrou. */
    record Marca(Instant em, UUID id) {
    }

    static Cursor ler(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return INICIO;
        }
        Cursor cursor = decodificar(texto);
        if (cursor == null) {
            throw new CampoInvalidoException("cursor", "Não foi possível continuar o feed. Recarregue a página.");
        }
        return cursor;
    }

    static Marca lerMarca(@Nullable String texto) {
        Cursor cursor = texto == null || texto.isBlank() ? null : decodificar(texto);
        if (cursor == null || cursor.em() == null || cursor.id() == null) {
            throw new CampoInvalidoException("desde", "Não foi possível conferir os posts novos. Recarregue a página.");
        }
        return new Marca(cursor.em(), cursor.id());
    }

    static String depoisDe(Instant em, UUID id) {
        return codificar("k|" + em + "|" + id);
    }

    static String marca(Instant em, UUID id) {
        return depoisDe(em, id);
    }

    static String deslocado(int deslocamento) {
        return codificar("d|" + deslocamento);
    }

    private static @Nullable Cursor decodificar(String texto) {
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
            // Quem chamou responde com a mensagem do seu campo.
        }
        return null;
    }

    private static String codificar(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
