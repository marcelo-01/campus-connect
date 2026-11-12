package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.campusconnect.conexao.service.ConexaoService;
import br.com.campusconnect.conexao.service.SituacaoConexao;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConexaoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConexaoService conexoes;

    private Conta ana;
    private Conta bia;
    private Conta caio;

    @BeforeEach
    void criarContas() throws Exception {
        jdbc.update("delete from conexao");
        jdbc.update("delete from bolha");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from usuario");
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");
        caio = cadastrar("Caio Lima", "caio");
    }

    // ------------------------------------------------------------ pedir e aceitar

    @Test
    void pedidoAceitoViraConexaoDosDoisLados() throws Exception {
        conectar(ana, bia).andExpect(status().isOk()).andExpect(situacao("PEDIDO_ENVIADO"));

        minhas(ana)
                .andExpect(jsonPath("$.enviados[*].username").value(contains("bia")))
                .andExpect(jsonPath("$.recebidos").isEmpty())
                .andExpect(jsonPath("$.conexoes").isEmpty());
        minhas(bia)
                .andExpect(jsonPath("$.recebidos[*].username").value(contains("ana.bc")))
                .andExpect(jsonPath("$.recebidos[0].id").value(ana.id().toString()))
                .andExpect(jsonPath("$.recebidos[0].nomeCompleto").value("Ana Beatriz"))
                .andExpect(jsonPath("$.recebidos[0].desde").isNotEmpty())
                .andExpect(jsonPath("$.enviados").isEmpty());

        // Aceitar é o mesmo "Conectar": agora as duas querem.
        conectar(bia, ana).andExpect(situacao("CONECTADOS"));

        minhas(ana)
                .andExpect(jsonPath("$.conexoes[*].username").value(contains("bia")))
                .andExpect(jsonPath("$.enviados").isEmpty());
        minhas(bia)
                .andExpect(jsonPath("$.conexoes[*].username").value(contains("ana.bc")))
                .andExpect(jsonPath("$.recebidos").isEmpty());
        totalDe(ana, caio).andExpect(jsonPath("$.total").value(1));
        totalDe(bia, caio).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void repetirAAcaoNaoMudaNada() throws Exception {
        conectar(ana, bia).andExpect(situacao("PEDIDO_ENVIADO"));
        conectar(ana, bia).andExpect(situacao("PEDIDO_ENVIADO"));
        minhas(bia).andExpect(jsonPath("$.recebidos", hasSize(1)));

        conectar(bia, ana).andExpect(situacao("CONECTADOS"));
        conectar(bia, ana).andExpect(situacao("CONECTADOS"));
        conectar(ana, bia).andExpect(situacao("CONECTADOS"));

        assertThat(statusDosRegistros()).containsExactly("ACEITA");
    }

    // ------------------------------------------------------------ recusa discreta

    @Test
    void quemPediuNaoFicaSabendoDaRecusaENaoConsegueInsistir() throws Exception {
        conectar(ana, bia);
        recusar(bia, ana).andExpect(situacao("NENHUMA"));

        minhas(bia).andExpect(jsonPath("$.recebidos").isEmpty());
        minhas(ana).andExpect(jsonPath("$.enviados[*].username").value(contains("bia")));

        conectar(ana, bia).andExpect(situacao("PEDIDO_ENVIADO"));
        minhas(bia).andExpect(jsonPath("$.recebidos").isEmpty());
    }

    @Test
    void quemRecusouPodeSeConectarDepoisEAConexaoSaiNaHora() throws Exception {
        conectar(ana, bia);
        recusar(bia, ana);

        conectar(bia, ana).andExpect(situacao("CONECTADOS"));

        minhas(ana).andExpect(jsonPath("$.conexoes[*].username").value(contains("bia")));
    }

    // ------------------------------------------------------------ cancelar e desfazer

    @Test
    void cancelarOPedidoTiraEleDosDoisLados() throws Exception {
        conectar(ana, bia);

        cancelar(ana, bia).andExpect(situacao("NENHUMA"));

        minhas(ana).andExpect(jsonPath("$.enviados").isEmpty());
        minhas(bia).andExpect(jsonPath("$.recebidos").isEmpty());
        assertThat(statusDosRegistros()).isEmpty();
    }

    @Test
    void desfazerAConexaoZeraOsDoisLadosEDeixaPedirDeNovo() throws Exception {
        conectar(ana, bia);
        conectar(bia, ana);

        desfazer(bia, ana).andExpect(situacao("NENHUMA"));

        minhas(ana).andExpect(jsonPath("$.conexoes").isEmpty());
        minhas(bia).andExpect(jsonPath("$.conexoes").isEmpty());
        totalDe(ana, ana).andExpect(jsonPath("$.total").value(0));
        conectar(bia, ana).andExpect(situacao("PEDIDO_ENVIADO"));
    }

    /** A tela pode estar desatualizada; cada ação só mexe no que o nome diz. */
    @Test
    void cadaAcaoSoMexeNoQueDiz() throws Exception {
        conectar(ana, bia);
        conectar(bia, ana);
        cancelar(ana, bia).andExpect(situacao("CONECTADOS"));
        recusar(ana, bia).andExpect(situacao("CONECTADOS"));

        conectar(ana, caio);
        desfazer(ana, caio).andExpect(situacao("PEDIDO_ENVIADO"));
        recusar(ana, caio).andExpect(situacao("PEDIDO_ENVIADO"));
        cancelar(caio, ana).andExpect(situacao("PEDIDO_RECEBIDO"));

        assertThat(statusDosRegistros()).containsExactlyInAnyOrder("ACEITA", "PENDENTE");
    }

    // ------------------------------------------------------------ erros

    @Test
    void naoSeConectaComAPropriaConta() throws Exception {
        conectar(ana, ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não é possível se conectar com a própria conta."));
        recusar(ana, ana).andExpect(status().isBadRequest());
        cancelar(ana, ana).andExpect(status().isBadRequest());
        desfazer(ana, ana).andExpect(status().isBadRequest());
    }

    @Test
    void pessoaQueNaoExisteOuNaoApareceDa404() throws Exception {
        UUID ninguem = UUID.randomUUID();
        comToken(post("/api/conexoes/com/" + ninguem), ana)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pessoa não encontrada."));
        comToken(get("/api/conexoes/usuario/" + ninguem), ana).andExpect(status().isNotFound());

        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", caio.id());
        conectar(ana, caio).andExpect(status().isNotFound());
        conexoesDe(caio, ana).andExpect(status().isNotFound());
        totalDe(caio, ana).andExpect(status().isNotFound());
        // A própria pessoa continua vendo a sua lista, como no perfil.
        conexoesDe(caio, caio).andExpect(status().isOk());
    }

    @Test
    void idQueNaoEhUuidDa400() throws Exception {
        comToken(post("/api/conexoes/com/nao-e-um-id"), ana).andExpect(status().isBadRequest());
    }

    @Test
    void semLoginNaoVeNemMexeEmConexoes() throws Exception {
        mvc.perform(get("/api/conexoes/minhas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conexoes/usuario/" + ana.id())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/conexoes/com/" + ana.id())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/conexoes/com/" + ana.id())).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ listas

    @Test
    void qualquerPessoaVeAsConexoesDeOutraSemEmailEAsMaisRecentesPrimeiro() throws Exception {
        conectar(ana, bia);
        conectar(bia, ana);
        conectar(ana, caio);
        // Um pedido pendente não aparece na lista.
        conexoesDe(ana, bia).andExpect(jsonPath("$[*].username").value(contains("bia")));

        conectar(caio, ana);

        conexoesDe(ana, bia)
                .andExpect(jsonPath("$[*].username").value(contains("caio", "bia")))
                .andExpect(jsonPath("$[0].id").value(caio.id().toString()))
                .andExpect(jsonPath("$[0].nomeCompleto").value("Caio Lima"))
                .andExpect(jsonPath("$[0].desde").isNotEmpty())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void contaQueNaoApareceSaiDasListasEDoTotal() throws Exception {
        conectar(ana, bia);
        conectar(bia, ana);
        conectar(caio, ana);
        jdbc.update("update usuario set status = 'SUSPENSO' where id in (?, ?)", bia.id(), caio.id());

        minhas(ana)
                .andExpect(jsonPath("$.conexoes").isEmpty())
                .andExpect(jsonPath("$.recebidos").isEmpty());
        conexoesDe(ana, ana).andExpect(jsonPath("$").isEmpty());
        totalDe(ana, ana).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void conexaoGravadaSemDataDeRespostaUsaADataDoPedido() throws Exception {
        jdbc.update("""
                insert into conexao (usuario_menor_id, usuario_maior_id, solicitante_id, status)
                values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid), ?, 'ACEITA')
                """, ana.id(), bia.id(), ana.id(), bia.id(), ana.id());

        conexoesDe(ana, ana).andExpect(jsonPath("$[0].desde").isNotEmpty());
        minhas(bia).andExpect(jsonPath("$.conexoes[0].desde").isNotEmpty());
    }

    // ------------------------------------------------------------ direto no service

    /** Nestes dois ids a ordem do Java é a oposta da do PostgreSQL, e o insert por SQL precisa passar no CHECK. */
    @Test
    void pedidoPorSqlRespeitaAOrdemDoPar() {
        UUID menor = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID maior = UUID.fromString("f0000000-0000-4000-8000-000000000002");
        usuarioComId(menor, "menor");
        usuarioComId(maior, "maior");

        assertThat(conexoes.conectar(maior, menor)).isEqualTo(SituacaoConexao.PEDIDO_ENVIADO);
        assertThat(conexoes.conectar(menor, maior)).isEqualTo(SituacaoConexao.CONECTADOS);
        assertThat(jdbc.queryForObject("select usuario_menor_id from conexao", UUID.class)).isEqualTo(menor);
    }

    @Test
    void pedidosCruzadosAoMesmoTempoViramUmaConexao() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            jdbc.update("delete from conexao");

            List<SituacaoConexao> respostas = aoMesmoTempo(
                    () -> conexoes.conectar(ana.id(), bia.id()),
                    () -> conexoes.conectar(bia.id(), ana.id()));

            assertThat(statusDosRegistros()).containsExactly("ACEITA");
            assertThat(respostas)
                    .containsExactlyInAnyOrder(SituacaoConexao.PEDIDO_ENVIADO, SituacaoConexao.CONECTADOS);
        }
    }

    @Test
    void cliqueDuploAoMesmoTempoDeixaUmPedidoSo() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            jdbc.update("delete from conexao");

            List<SituacaoConexao> respostas = aoMesmoTempo(
                    () -> conexoes.conectar(ana.id(), bia.id()),
                    () -> conexoes.conectar(ana.id(), bia.id()));

            assertThat(statusDosRegistros()).containsExactly("PENDENTE");
            assertThat(respostas).containsOnly(SituacaoConexao.PEDIDO_ENVIADO);
        }
    }

    // ------------------------------------------------------------ apoio

    private record Conta(UUID id, String token) {
    }

    private Conta cadastrar(String nome, String username) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "%s", "username": "%s", "email": "%s@exemplo.com", "senha": "senha-forte-123"}
                        """.formatted(nome, username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Conta(UUID.fromString(JsonPath.read(corpo, "$.usuario.id")), JsonPath.read(corpo, "$.accessToken"));
    }

    /** O Hibernate gera o id no persist, então um id escolhido precisa entrar por SQL. */
    private void usuarioComId(UUID id, String nome) {
        jdbc.update("""
                insert into usuario (id, username, nome_completo, email, senha_hash, status)
                values (?, ?, ?, ?, 'hash', 'ATIVO')
                """, id, nome, nome, nome + "@exemplo.com");
    }

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private ResultActions conectar(Conta quem, Conta outro) throws Exception {
        return comToken(post("/api/conexoes/com/" + outro.id()), quem);
    }

    private ResultActions recusar(Conta quem, Conta outro) throws Exception {
        return comToken(post("/api/conexoes/com/" + outro.id() + "/recusa"), quem);
    }

    private ResultActions cancelar(Conta quem, Conta outro) throws Exception {
        return comToken(delete("/api/conexoes/com/" + outro.id() + "/pedido"), quem);
    }

    private ResultActions desfazer(Conta quem, Conta outro) throws Exception {
        return comToken(delete("/api/conexoes/com/" + outro.id()), quem);
    }

    private ResultActions minhas(Conta quem) throws Exception {
        return comToken(get("/api/conexoes/minhas"), quem);
    }

    private ResultActions conexoesDe(Conta pessoa, Conta quemVe) throws Exception {
        return comToken(get("/api/conexoes/usuario/" + pessoa.id()), quemVe);
    }

    private ResultActions totalDe(Conta pessoa, Conta quemVe) throws Exception {
        return comToken(get("/api/conexoes/usuario/" + pessoa.id() + "/total"), quemVe);
    }

    private static ResultMatcher situacao(String esperada) {
        return jsonPath("$.situacao").value(esperada);
    }

    private List<String> statusDosRegistros() {
        return jdbc.queryForList("select status from conexao", String.class);
    }

    /** Solta as ações juntas, cada uma na sua thread e na sua transação, e espera todas terminarem. */
    @SafeVarargs
    private static List<SituacaoConexao> aoMesmoTempo(Callable<SituacaoConexao>... acoes) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(acoes.length)) {
            List<Future<SituacaoConexao>> emAndamento = new ArrayList<>();
            for (Callable<SituacaoConexao> acao : acoes) {
                emAndamento.add(threads.submit(() -> {
                    largada.await();
                    return acao.call();
                }));
            }
            largada.countDown();
            List<SituacaoConexao> respostas = new ArrayList<>();
            for (Future<SituacaoConexao> acao : emAndamento) {
                respostas.add(acao.get(10, TimeUnit.SECONDS));
            }
            return respostas;
        }
    }
}
