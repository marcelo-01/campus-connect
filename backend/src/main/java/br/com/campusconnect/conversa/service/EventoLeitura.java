package br.com.campusconnect.conversa.service;

import java.time.Instant;
import java.util.UUID;

/**
 * O dado do evento ao vivo {@code leitura}: {@code leitorId} leu a conversa até {@code ate}. Para a outra pessoa, é o
 * "Visto"; para as outras abas de quem leu, a conversa deixa de estar não lida.
 */
public record EventoLeitura(UUID conversaId, UUID leitorId, Instant ate) {
}
