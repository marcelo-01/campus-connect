package br.com.campusconnect.conexao.service;

import br.com.campusconnect.comum.OrdemUuid;
import br.com.campusconnect.conexao.model.StatusConexao;
import java.util.UUID;

/** Como cada lado enxerga um registro de conexão, e a ordem do par no banco. */
final class RegrasConexao {

    private RegrasConexao() {
    }

    /**
     * A recusa é discreta: quem pediu continua vendo o pedido como enviado, e quem recusou deixa de ver qualquer
     * coisa. Assim quem pediu não fica sabendo da recusa e não consegue insistir.
     *
     * @param quem quem está vendo
     * @param solicitante quem fez o pedido que criou o registro
     */
    static SituacaoConexao vistaPor(UUID quem, StatusConexao status, UUID solicitante) {
        boolean pediu = quem.equals(solicitante);
        return switch (status) {
            case ACEITA -> SituacaoConexao.CONECTADOS;
            case PENDENTE -> pediu ? SituacaoConexao.PEDIDO_ENVIADO : SituacaoConexao.PEDIDO_RECEBIDO;
            case RECUSADA -> pediu ? SituacaoConexao.PEDIDO_ENVIADO : SituacaoConexao.NENHUMA;
            // O bloqueio ainda não existe; quando existir, cada lado vai ver uma coisa diferente.
            case BLOQUEADA -> SituacaoConexao.NENHUMA;
        };
    }

    /** As duas pessoas na ordem do CHECK {@code usuario_menor_id < usuario_maior_id}. */
    record Par(UUID menor, UUID maior) {

        /** @throws ConexaoConsigoMesmoException se as duas forem a mesma pessoa */
        static Par de(UUID quem, UUID outro) {
            if (quem.equals(outro)) {
                throw new ConexaoConsigoMesmoException();
            }
            return OrdemUuid.vemAntes(quem, outro) ? new Par(quem, outro) : new Par(outro, quem);
        }
    }
}
