package br.com.campusconnect.conexao.service;

import java.util.List;

/**
 * Tudo o que liga quem está logado a outras pessoas, cada lista com as mais recentes primeiro. O frontend tira daqui
 * a situação de cada botão de conexão e o contador de pedidos do menu.
 *
 * @param recebidos pedidos que a pessoa ainda não respondeu; os que ela recusou não voltam
 * @param enviados pedidos que a pessoa fez e que ainda não viraram conexão, inclusive os recusados, porque a recusa
 *     é discreta
 */
public record MinhasConexoes(List<PessoaNaConexao> conexoes, List<PessoaNaConexao> recebidos,
        List<PessoaNaConexao> enviados) {
}
