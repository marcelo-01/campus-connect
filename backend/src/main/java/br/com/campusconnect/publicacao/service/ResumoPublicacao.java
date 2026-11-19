package br.com.campusconnect.publicacao.service;

import java.util.UUID;

/**
 * Uma publicação mostrada de passagem, como nas notificações.
 *
 * @param trecho o começo do texto, numa linha só; vazio se a publicação é só de imagem ou vídeo
 */
public record ResumoPublicacao(UUID id, String trecho) {

    /** Em caracteres (um emoji conta como um). */
    static final int TAMANHO_TRECHO = 140;

    static String trecho(String conteudo) {
        String linha = conteudo.replaceAll("\\s+", " ").strip();
        if (linha.codePointCount(0, linha.length()) <= TAMANHO_TRECHO) {
            return linha;
        }
        return linha.substring(0, linha.offsetByCodePoints(0, TAMANHO_TRECHO)).stripTrailing() + "…";
    }
}
