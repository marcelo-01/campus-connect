package br.com.campusconnect.conexao.model;

/** O bloqueio não é um status: ele tem tabela própria ({@link Bloqueio}), porque cada lado bloqueia por si. */
public enum StatusConexao {
    PENDENTE,
    ACEITA,
    RECUSADA
}
