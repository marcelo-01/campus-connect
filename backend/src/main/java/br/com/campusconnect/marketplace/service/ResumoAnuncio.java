package br.com.campusconnect.marketplace.service;

import java.util.UUID;

/** Um anúncio mostrado de passagem, como nas notificações. */
public record ResumoAnuncio(UUID id, String titulo) {
}
