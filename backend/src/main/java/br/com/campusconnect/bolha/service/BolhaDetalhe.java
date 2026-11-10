package br.com.campusconnect.bolha.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A página de uma bolha.
 *
 * @param membro se quem está vendo participa desta bolha
 * @param caminho as bolhas acima, do topo até a mãe; vazio se esta fica no topo
 * @param filhas as bolhas logo abaixo, na ordem de exibição
 */
public record BolhaDetalhe(UUID id, String nome, String slug, String sigla, String tipo, String fotoUrl,
        String descricao, String endereco, long totalMembros, boolean membro, Instant criadaEm,
        List<BolhaResumo> caminho, List<BolhaResumo> filhas) {
}
