package br.com.campusconnect.bolha.model;

import org.jspecify.annotations.Nullable;

/**
 * O nível de uma bolha na hierarquia. Nada aqui é oficialmente de uma instituição: o tipo só diz onde a
 * bolha pode ficar. A ordem das constantes é a ordem em que as irmãs aparecem nas listas.
 */
public enum TipoBolha {

    /** O topo, como "IFBA". Não fica dentro de nenhuma outra. */
    GERAL,

    /** Fica dentro de uma bolha geral. */
    CAMPUS,

    /** Fica dentro de um campus. */
    CURSO,

    /** Grupo de interesse: no topo ou dentro de qualquer bolha que não seja outra comunidade. */
    COMUNIDADE;

    /** @param pai tipo da bolha acima, ou {@code null} para ficar no topo */
    public boolean podeFicarDentroDe(@Nullable TipoBolha pai) {
        return switch (this) {
            case GERAL -> pai == null;
            case CAMPUS -> pai == GERAL;
            case CURSO -> pai == CAMPUS;
            case COMUNIDADE -> pai != COMUNIDADE;
        };
    }
}
