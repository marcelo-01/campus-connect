package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Os chips do feed. */
enum OrdemFeed {

    /** Do mais novo para o mais antigo. */
    RECENTES,

    /** Os mais curtidos dos últimos 7 dias. */
    ALTA,

    /** Só os que têm imagem ou vídeo, do mais novo para o mais antigo. */
    MIDIA;

    static OrdemFeed de(@Nullable String valor) {
        if (valor == null || valor.isBlank()) {
            return RECENTES;
        }
        try {
            return valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecida) {
            throw new CampoInvalidoException("ordem", "Use recentes, alta ou midia.");
        }
    }
}
