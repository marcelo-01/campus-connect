package br.com.campusconnect.aovivo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * As conexões aqui não estão ligadas a uma resposta HTTP: o que é enviado antes disso fica guardado, e depois de
 * {@code complete()} um envio falha. É essa falha que mostra quando o canal tentou entregar um evento.
 */
class CanalAoVivoTest {

    private static final Instant AGORA = Instant.parse("2026-11-16T22:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BIA = UUID.randomUUID();

    private final CanalAoVivo canal = new CanalAoVivo(
            new PropriedadesAoVivo(Duration.ofMinutes(30), Duration.ofHours(1), 5),
            Clock.fixed(AGORA, ZoneOffset.UTC));

    @AfterEach
    void desligar() {
        canal.aoDesligar();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aConexaoNaoDuraMaisQueOJwt() {
        assertThat(canal.abrir(ANA, AGORA.plus(Duration.ofMinutes(10))).getTimeout())
                .isEqualTo(Duration.ofMinutes(10).toMillis());
        assertThat(canal.abrir(ANA, AGORA.plus(Duration.ofHours(2))).getTimeout())
                .isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(canal.abrir(ANA, null).getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(canal.abrir(ANA, AGORA.minusSeconds(5)).getTimeout()).isEqualTo(1000L);
    }

    @Test
    void aSextaAbaFechaAMaisAntiga() throws Exception {
        SseEmitter primeira = canal.abrir(ANA, null);
        for (int aba = 2; aba <= 6; aba++) {
            canal.abrir(ANA, null);
        }

        assertThat(canal.abertasDe(ANA)).isEqualTo(5);
        assertThatThrownBy(() -> primeira.send("depois")).isInstanceOf(IllegalStateException.class);
        assertThat(canal.abertasDe(BIA)).isZero();
    }

    @Test
    void enviarNuncaLancaEDescartaAConexaoQueFalha() {
        SseEmitter fechada = canal.abrir(ANA, null);
        canal.abrir(ANA, null);
        fechada.complete();

        assertThatCode(() -> canal.enviar(List.of(ANA, BIA), "mensagem", Map.of("texto", "Oi")))
                .doesNotThrowAnyException();

        assertThat(canal.abertasDe(ANA)).isEqualTo(1);
    }

    @Test
    void dentroDeUmaTransacaoOEventoEsperaOCommit() {
        canal.abrir(ANA, null).complete();
        TransactionSynchronizationManager.initSynchronization();

        canal.enviar(List.of(ANA), "mensagem", Map.of());
        assertThat(canal.abertasDe(ANA)).as("antes do commit, nada foi enviado").isEqualTo(1);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(canal.abertasDe(ANA)).as("depois do commit, o envio falhou e a conexão saiu").isZero();
    }

    @Test
    void umaTransacaoDesfeitaNaoEnviaNada() {
        canal.abrir(ANA, null).complete();
        TransactionSynchronizationManager.initSynchronization();

        canal.enviar(List.of(ANA), "mensagem", Map.of());
        TransactionSynchronizationManager.getSynchronizations().forEach(sincronizacao ->
                sincronizacao.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(canal.abertasDe(ANA)).isEqualTo(1);
    }

    @Test
    void encerrarTodasEsvaziaORegistro() {
        SseEmitter conexao = canal.abrir(ANA, null);
        canal.abrir(BIA, null);

        canal.encerrarTodas();

        assertThat(canal.abertasDe(ANA)).isZero();
        assertThat(canal.abertasDe(BIA)).isZero();
        assertThatThrownBy(() -> conexao.send("depois")).isInstanceOf(IllegalStateException.class);
    }
}
