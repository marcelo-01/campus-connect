package br.com.campusconnect.marketplace.service;

import java.util.UUID;

/**
 * O que acontece com anúncios e avaliações e interessa a outros módulos, como o de notificações. Sai dentro da
 * transação de quem agiu.
 *
 * <p>Favoritar não publica nada, porque é privado. Trocar a nota de uma avaliação também não: quem anunciou já foi
 * avisado dela.
 */
public final class EventosMarketplace {

    private EventosMarketplace() {
    }

    /** Uma avaliação nova, a primeira de {@code autorId} nesse anúncio. */
    public record AnuncioAvaliado(UUID avaliacaoId, UUID anuncioId, UUID anuncianteId, UUID autorId) {
    }

    public record AvaliacaoApagada(UUID avaliacaoId) {
    }

    /** O anúncio saiu de vez. Pausar não conta: quem anunciou ainda vê o anúncio pausado. */
    public record AnuncioExcluido(UUID anuncioId) {
    }
}
