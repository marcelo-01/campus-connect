package br.com.campusconnect.conexao.service;

import java.util.UUID;

/**
 * O que acontece entre duas pessoas e interessa a outros módulos, como o de notificações. Sai dentro da transação de
 * quem agiu, e só quando algo mudou: repetir uma ação não publica de novo.
 *
 * <p>A recusa não publica nada, porque é discreta: quem pediu não pode descobrir.
 */
public final class EventosConexao {

    private EventosConexao() {
    }

    /** Um pedido novo, pendente. O pedido de quem foi bloqueado nasce recusado e não publica nada. */
    public record PedidoFeito(UUID solicitante, UUID destinatario) {
    }

    /** Quem pediu retirou o pedido antes de ele virar conexão. */
    public record PedidoRetirado(UUID solicitante, UUID destinatario) {
    }

    /** O pedido do {@code solicitante} virou conexão, aceito por {@code quemAceitou}. */
    public record PedidoAceito(UUID solicitante, UUID quemAceitou) {
    }

    /** {@code quem} bloqueou {@code bloqueado}. */
    public record PessoaBloqueada(UUID quem, UUID bloqueado) {
    }
}
