package br.com.campusconnect.comum;

import java.util.UUID;

/**
 * Ordena UUIDs como o PostgreSQL: byte a byte, sem sinal.
 *
 * <p>{@link UUID#compareTo} compara {@code long} com sinal e discorda dessa ordem em cerca de
 * metade dos pares, o que violaria os CHECKs {@code usuario_menor_id < usuario_maior_id}.
 */
public final class OrdemUuid {

    private OrdemUuid() {
    }

    /** Indica se {@code a} vem antes de {@code b}. Os dois precisam existir e ser diferentes. */
    public static boolean vemAntes(UUID a, UUID b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Os dois usuários precisam estar salvos para formar um par");
        }
        int ordem = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (ordem == 0) {
            ordem = Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
        }
        if (ordem == 0) {
            throw new IllegalArgumentException("Um par precisa de dois usuários diferentes");
        }
        return ordem < 0;
    }
}
