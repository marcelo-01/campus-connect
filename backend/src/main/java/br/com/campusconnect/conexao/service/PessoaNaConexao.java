package br.com.campusconnect.conexao.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma pessoa numa lista de conexões ou de pedidos. Só dados públicos: nada de e-mail.
 *
 * @param id id do usuário
 * @param fotoUrl caminho da foto de perfil, ou {@code null} para mostrar as iniciais
 * @param desde numa conexão, quando o pedido foi aceito; num pedido, quando foi feito
 */
public record PessoaNaConexao(UUID id, String username, String nomeCompleto, String fotoUrl, Instant desde) {
}
