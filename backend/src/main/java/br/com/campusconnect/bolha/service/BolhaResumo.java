package br.com.campusconnect.bolha.service;

import java.util.UUID;

/**
 * Bolha numa lista: o Explorar, o menu lateral, o perfil, as sub-bolhas de uma página.
 *
 * @param tipo {@code GERAL}, {@code CAMPUS}, {@code CURSO} ou {@code COMUNIDADE}
 * @param sigla o que a pessoa cadastrou, ou {@code null}; o frontend tira uma do nome
 * @param bolhaPaiId bolha acima, ou {@code null} se esta fica no topo
 * @param nivel profundidade na árvore: 0 no topo, 1 logo abaixo, e assim por diante
 * @param membro se quem está vendo participa desta bolha
 */
public record BolhaResumo(UUID id, String nome, String slug, String sigla, String tipo, String fotoUrl,
        String descricao, UUID bolhaPaiId, int nivel, long totalMembros, boolean membro) {
}
