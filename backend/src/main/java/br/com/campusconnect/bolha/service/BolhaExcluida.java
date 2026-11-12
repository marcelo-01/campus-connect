package br.com.campusconnect.bolha.service;

import java.util.UUID;

/**
 * Publicado por {@link BolhaService#excluir} dentro da transação, logo antes de apagar a bolha. Quem
 * guarda dados presos a ela (as publicações, por exemplo) ouve com um listener síncrono e apaga os seus
 * na mesma transação: se a exclusão falhar, nada é apagado.
 */
public record BolhaExcluida(UUID bolhaId) {
}
