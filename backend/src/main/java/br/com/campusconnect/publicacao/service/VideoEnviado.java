package br.com.campusconnect.publicacao.service;

import org.springframework.core.io.InputStreamSource;

/**
 * Um vídeo recebido no upload. O conteúdo é lido em fluxo, sem carregar os 50 MB na memória.
 *
 * @param tamanho tamanho informado pelo upload
 */
public record VideoEnviado(long tamanho, InputStreamSource conteudo) {
}
