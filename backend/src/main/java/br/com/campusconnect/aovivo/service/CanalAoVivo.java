package br.com.campusconnect.aovivo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * As conexões SSE abertas, por pessoa, e o envio de eventos para elas.
 *
 * <p>Cada conexão começa com o evento {@value #PRONTO}, que avisa o cliente para buscar o que perdeu enquanto estava
 * desconectado: um evento enviado sem ninguém ouvindo se perde, e o cliente sempre tira o atraso pela API.
 *
 * <p>O registro fica na memória, então só funciona com uma instância do backend. Com mais de uma, os eventos teriam
 * de passar por algo compartilhado, como o LISTEN/NOTIFY do PostgreSQL.
 */
@Slf4j
@Service
public class CanalAoVivo {

    /** Evento que abre toda conexão. */
    public static final String PRONTO = "pronto";

    /** Evento que fecha a conexão mais antiga quando a pessoa passa do limite de abas. */
    public static final String ENCERRADA = "encerrada";

    private static final Duration DURACAO_MINIMA = Duration.ofSeconds(1);

    private final PropriedadesAoVivo propriedades;
    private final Clock relogio;
    private final Map<UUID, List<SseEmitter>> abertas = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pulsos;
    private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    CanalAoVivo(PropriedadesAoVivo propriedades) {
        this(propriedades, Clock.systemUTC());
    }

    CanalAoVivo(PropriedadesAoVivo propriedades, Clock relogio) {
        this.propriedades = propriedades;
        this.relogio = relogio;
        // Thread daemon: não segura a JVM aberta. O @EnableScheduling traria um agendador para a aplicação inteira.
        this.pulsos = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("aovivo-pulso").factory());
        long intervalo = propriedades.pulso().toMillis();
        pulsos.scheduleAtFixedRate(this::pulsar, intervalo, intervalo, TimeUnit.MILLISECONDS);
    }

    /**
     * Abre uma conexão para a pessoa. Ela dura até {@code expiraEm} (a validade do JWT) ou até a duração máxima, o
     * que vier antes.
     *
     * @param expiraEm a validade do JWT; sem ela, vale só a duração máxima
     */
    public SseEmitter abrir(UUID usuario, @Nullable Instant expiraEm) {
        Duration ateExpirar = expiraEm != null ? Duration.between(relogio.instant(), expiraEm)
                : propriedades.duracaoMaxima();
        Duration duracao = maximo(DURACAO_MINIMA, minimo(propriedades.duracaoMaxima(), ateExpirar));
        SseEmitter conexao = new SseEmitter(duracao.toMillis());
        // Sem isso, o Spring transforma o fim do tempo num erro e tenta responder 503 numa resposta já enviada.
        conexao.onTimeout(conexao::complete);
        conexao.onCompletion(() -> remover(usuario, conexao));
        conexao.onError(erro -> remover(usuario, conexao));

        List<SseEmitter> substituidas = new ArrayList<>();
        abertas.compute(usuario, (id, lista) -> {
            List<SseEmitter> conexoes = lista != null ? lista : new CopyOnWriteArrayList<>();
            conexoes.add(conexao);
            while (conexoes.size() > propriedades.maximoPorPessoa()) {
                substituidas.add(conexoes.removeFirst());
            }
            return conexoes;
        });
        substituidas.forEach(antiga -> {
            // O cliente não reconecta sozinho depois disso, para duas abas não ficarem se derrubando.
            entregar(usuario, antiga, SseEmitter.event().name(ENCERRADA).data(Map.of("motivo", "substituida")));
            fechar(antiga);
        });

        entregar(usuario, conexao, SseEmitter.event().name(PRONTO).data(Map.of()));
        return conexao;
    }

    /**
     * Manda o evento para todas as conexões das pessoas. Dentro de uma transação, o envio espera o commit: um evento
     * nunca sai de algo que foi desfeito.
     *
     * <p>Nunca lança exceção: uma conexão que falha é descartada, e o cliente tira o atraso quando reconectar. Uma
     * exceção depois do commit chegaria a quem chamou como erro, com tudo já gravado.
     *
     * @param dados o conteúdo do evento, já pronto: depois do commit não há mais sessão do Hibernate aberta
     */
    public void enviar(Collection<UUID> usuarios, String evento, Object dados) {
        List<UUID> destinatarios = List.copyOf(usuarios);
        Runnable envio = () -> destinatarios.forEach(usuario -> conexoesDe(usuario)
                .forEach(conexao -> entregar(usuario, conexao, SseEmitter.event().name(evento).data(dados))));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }

    /** Quantas conexões a pessoa tem abertas. */
    public int abertasDe(UUID usuario) {
        return conexoesDe(usuario).size();
    }

    /** Fecha todas as conexões abertas. */
    public void encerrarTodas() {
        List<SseEmitter> todas = abertas.values().stream().flatMap(List::stream).toList();
        abertas.clear();
        todas.forEach(CanalAoVivo::fechar);
    }

    /** Sem isso, o desligamento gracioso esperaria cada conexão até o fim do prazo dela. */
    @EventListener(ContextClosedEvent.class)
    void aoDesligar() {
        pulsos.shutdownNow();
        encerrarTodas();
        envios.shutdownNow();
    }

    // ------------------------------------------------------------ apoio

    private List<SseEmitter> conexoesDe(UUID usuario) {
        List<SseEmitter> conexoes = abertas.get(usuario);
        return conexoes != null ? List.copyOf(conexoes) : List.of();
    }

    private void remover(UUID usuario, SseEmitter conexao) {
        abertas.computeIfPresent(usuario, (id, conexoes) -> {
            conexoes.remove(conexao);
            return conexoes.isEmpty() ? null : conexoes;
        });
    }

    /** Uma conexão que falha (aba fechada, rede caída, já completada) é só descartada. */
    private void entregar(UUID usuario, SseEmitter conexao, SseEmitter.SseEventBuilder evento) {
        try {
            conexao.send(evento);
        } catch (Exception erro) {
            log.debug("Conexão ao vivo descartada", erro);
            remover(usuario, conexao);
        }
    }

    private static void fechar(SseEmitter conexao) {
        try {
            conexao.complete();
        } catch (RuntimeException erro) {
            log.debug("Conexão ao vivo já estava fechada", erro);
        }
    }

    /** O comentário vazio de cada conexão sai numa thread própria: um cliente travado não atrasa os outros. */
    private void pulsar() {
        try {
            abertas.forEach((usuario, conexoes) -> List.copyOf(conexoes).forEach(conexao -> envios.execute(
                    () -> entregar(usuario, conexao, SseEmitter.event().comment("")))));
        } catch (RuntimeException erro) {
            // Uma exceção aqui cancelaria os próximos pulsos.
            log.warn("Falha ao manter as conexões ao vivo", erro);
        }
    }

    private static Duration minimo(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static Duration maximo(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
