package br.com.campusconnect.conversa.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.comum.OrdemUuid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** O texto de uma mensagem, a prévia na lista e a ordem do par no banco. */
final class RegrasConversa {

    /** O banco tem o mesmo limite ({@code ck_mensagem_tamanho}). */
    static final int TAMANHO_MAXIMO = 2000;

    static final int TAMANHO_PREVIA = 120;

    private RegrasConversa() {
    }

    /** O texto sem espaços nas pontas. Vazio ou longo demais dá 400. */
    static String conteudo(@Nullable String texto) {
        String limpo = texto == null ? "" : texto.strip();
        if (limpo.isEmpty()) {
            throw new CampoInvalidoException("conteudo", "Escreva uma mensagem.");
        }
        if (limpo.codePointCount(0, limpo.length()) > TAMANHO_MAXIMO) {
            throw new CampoInvalidoException("conteudo",
                    "A mensagem pode ter até " + TAMANHO_MAXIMO + " caracteres.");
        }
        return limpo;
    }

    /** O começo da mensagem, numa linha só, para a lista de conversas. */
    static @Nullable String previa(@Nullable String conteudo) {
        if (conteudo == null) {
            return null;
        }
        String linha = conteudo.replaceAll("\\s+", " ").strip();
        if (linha.codePointCount(0, linha.length()) <= TAMANHO_PREVIA) {
            return linha;
        }
        int corte = linha.offsetByCodePoints(0, TAMANHO_PREVIA);
        return linha.substring(0, corte).stripTrailing() + "…";
    }

    /** As duas pessoas na ordem do CHECK {@code usuario_menor_id < usuario_maior_id}. */
    record Par(UUID menor, UUID maior) {

        /** @throws ConversaConsigoMesmoException se as duas forem a mesma pessoa */
        static Par de(UUID quem, UUID outro) {
            if (quem.equals(outro)) {
                throw new ConversaConsigoMesmoException();
            }
            return OrdemUuid.vemAntes(quem, outro) ? new Par(quem, outro) : new Par(outro, quem);
        }
    }
}
