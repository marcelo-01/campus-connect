package br.com.campusconnect.conexao.service;

/**
 * Como quem está logado enxerga a relação com outra pessoa. Não é o status gravado no banco: um pedido recusado
 * continua aparecendo como {@link #PEDIDO_ENVIADO} para quem pediu (a recusa é discreta) e some para quem recusou.
 */
public enum SituacaoConexao {
    /** Nada entre as duas pessoas: o botão oferece "Conectar". */
    NENHUMA,
    /** Quem vê fez o pedido, e a outra pessoa ainda não aceitou. */
    PEDIDO_ENVIADO,
    /** A outra pessoa fez o pedido, e quem vê ainda não respondeu. */
    PEDIDO_RECEBIDO,
    CONECTADOS
}
