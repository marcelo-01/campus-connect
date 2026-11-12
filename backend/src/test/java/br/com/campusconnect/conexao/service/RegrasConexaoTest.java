package br.com.campusconnect.conexao.service;

import static br.com.campusconnect.conexao.model.StatusConexao.ACEITA;
import static br.com.campusconnect.conexao.model.StatusConexao.BLOQUEADA;
import static br.com.campusconnect.conexao.model.StatusConexao.PENDENTE;
import static br.com.campusconnect.conexao.model.StatusConexao.RECUSADA;
import static br.com.campusconnect.conexao.service.RegrasConexao.vistaPor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.campusconnect.conexao.service.RegrasConexao.Par;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegrasConexaoTest {

    /** Nestes dois ids a ordem do Java ({@link UUID#compareTo}) é a oposta da do PostgreSQL. */
    private static final UUID ANA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BIA = UUID.fromString("f0000000-0000-4000-8000-000000000002");

    /** Em todos os casos, quem fez o pedido foi a Ana. */
    @Test
    void cadaLadoEnxergaORegistroDoSeuJeito() {
        assertThat(vistaPor(ANA, PENDENTE, ANA)).isEqualTo(SituacaoConexao.PEDIDO_ENVIADO);
        assertThat(vistaPor(BIA, PENDENTE, ANA)).isEqualTo(SituacaoConexao.PEDIDO_RECEBIDO);

        // Recusa discreta: a Ana continua vendo o pedido; a Bia, que recusou, não vê nada.
        assertThat(vistaPor(ANA, RECUSADA, ANA)).isEqualTo(SituacaoConexao.PEDIDO_ENVIADO);
        assertThat(vistaPor(BIA, RECUSADA, ANA)).isEqualTo(SituacaoConexao.NENHUMA);

        assertThat(vistaPor(ANA, ACEITA, ANA)).isEqualTo(SituacaoConexao.CONECTADOS);
        assertThat(vistaPor(BIA, ACEITA, ANA)).isEqualTo(SituacaoConexao.CONECTADOS);

        // O bloqueio ainda não existe.
        assertThat(vistaPor(ANA, BLOQUEADA, ANA)).isEqualTo(SituacaoConexao.NENHUMA);
        assertThat(vistaPor(BIA, BLOQUEADA, ANA)).isEqualTo(SituacaoConexao.NENHUMA);
    }

    @Test
    void oParSegueAOrdemDoPostgres() {
        assertThat(BIA).isLessThan(ANA);

        assertThat(Par.de(BIA, ANA)).isEqualTo(new Par(ANA, BIA));
        assertThat(Par.de(ANA, BIA)).isEqualTo(new Par(ANA, BIA));
    }

    @Test
    void naoMontaParComAMesmaPessoa() {
        assertThatThrownBy(() -> Par.de(ANA, ANA)).isInstanceOf(ConexaoConsigoMesmoException.class);
    }
}
