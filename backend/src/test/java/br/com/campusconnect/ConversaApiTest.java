package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import br.com.campusconnect.conexao.service.ConexaoService;
import br.com.campusconnect.conversa.service.ConversaService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConversaApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConversaService conversas;

    @Autowired
    private ConexaoService conexoes;

    @Autowired
    private CanalAoVivo canal;

    private Conta ana;
    private Conta bia;
    private Conta caio;

    @BeforeEach
    void criarContas() throws Exception {
        // O cascade leva junto as conversas, as mensagens e os bloqueios.
        jdbc.execute("truncate usuario, bolha cascade");
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");
        caio = cadastrar("Caio Lima", "caio");
    }

    @AfterEach
    void fecharConexoesAoVivo() {
        canal.encerrarTodas();
    }

    // ------------------------------------------------------------ abrir

    @Test
    void abrirDuasVezesDevolveAMesmaConversaQueSoApareceDepoisDaPrimeiraMensagem() throws Exception {
        String id = idDe(abrir(ana, bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoa.username").value("bia"))
                .andExpect(jsonPath("$.pessoa.email").doesNotExist())
                .andExpect(jsonPath("$.podeEnviar").value(true))
                .andExpect(jsonPath("$.bloqueei").value(false))
                .andExpect(jsonPath("$.outroLeuEm").value(nullValue())));

        abrir(bia, ana).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.pessoa.username").value("ana.bc"));
        listar(ana, "").andExpect(jsonPath("$.itens").isEmpty());
        listar(bia, "").andExpect(jsonPath("$.itens").isEmpty());

        enviar(ana, id, "Oi, Bia!").andExpect(status().isCreated());

        listar(bia, "").andExpect(jsonPath("$.itens[*].id").value(contains(id)));
    }

    @Test
    void abrirAoMesmoTempoDosDoisLadosCriaUmaConversaSo() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            jdbc.update("delete from conversa");

            List<Object> respostas = aoMesmoTempo(
                    () -> conversas.abrirCom(ana.id(), bia.id()).id(),
                    () -> conversas.abrirCom(bia.id(), ana.id()).id());

            assertThat(respostas.get(0)).isEqualTo(respostas.get(1));
            assertThat(jdbc.queryForObject("select count(*) from conversa", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from participante_conversa", Long.class)).isEqualTo(2);
        }
    }

    @Test
    void naoAbreComAPropriaContaNemComQuemNaoAparece() throws Exception {
        abrir(ana, ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não é possível conversar com a própria conta."));
        comToken(post("/api/conversas/com/" + UUID.randomUUID()), ana)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pessoa não encontrada."));

        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", caio.id());
        abrir(ana, caio).andExpect(status().isNotFound());
    }

    @Test
    void comBloqueioNaoComecaConversaNovaMasAAntigaContinuaAbrindo() throws Exception {
        String antiga = idDe(abrir(ana, bia));
        enviar(ana, antiga, "Oi").andExpect(status().isCreated());

        conexoes.bloquear(bia.id(), ana.id());
        conexoes.bloquear(caio.id(), ana.id());

        abrir(ana, caio)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Não é possível enviar mensagens para esta pessoa."));
        abrir(caio, ana).andExpect(status().isForbidden());

        // Quem foi bloqueado não pode responder; quem bloqueou vê que bloqueou.
        abrir(ana, bia)
                .andExpect(jsonPath("$.id").value(antiga))
                .andExpect(jsonPath("$.podeEnviar").value(false))
                .andExpect(jsonPath("$.bloqueei").value(false));
        buscar(bia, antiga)
                .andExpect(jsonPath("$.podeEnviar").value(false))
                .andExpect(jsonPath("$.bloqueei").value(true));
        enviar(ana, antiga, "Oi de novo").andExpect(status().isForbidden());
        enviar(bia, antiga, "Tchau").andExpect(status().isForbidden());

        conexoes.desbloquear(bia.id(), ana.id());
        enviar(bia, antiga, "Voltei").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ lista

    @Test
    void aListaVemPelaUltimaMensagemComPreviaENaoLida() throws Exception {
        String comBia = idDe(abrir(ana, bia));
        String comCaio = idDe(abrir(ana, caio));
        enviar(ana, comBia, "Oi, Bia!");
        enviar(caio, comCaio, "Oi, Ana!\nTudo bem?");

        listar(ana, "")
                .andExpect(jsonPath("$.itens[*].id").value(contains(comCaio, comBia)))
                .andExpect(jsonPath("$.itens[0].pessoa.nomeCompleto").value("Caio Lima"))
                .andExpect(jsonPath("$.itens[0].ultima.texto").value("Oi, Ana! Tudo bem?"))
                .andExpect(jsonPath("$.itens[0].ultima.minha").value(false))
                .andExpect(jsonPath("$.itens[0].naoLida").value(true))
                .andExpect(jsonPath("$.itens[1].ultima.minha").value(true))
                .andExpect(jsonPath("$.itens[1].naoLida").value(false))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
        listar(ana, "?filtro=nao-lidas").andExpect(jsonPath("$.itens[*].id").value(contains(comCaio)));
        naoLidas(ana).andExpect(jsonPath("$.total").value(1));
        naoLidas(bia).andExpect(jsonPath("$.total").value(1));

        // Uma mensagem nova sobe a conversa.
        enviar(bia, comBia, "Oi, Ana!");
        listar(ana, "").andExpect(jsonPath("$.itens[*].id").value(contains(comBia, comCaio)));
        naoLidas(ana).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void aBuscaProcuraNoNomeENoUsername() throws Exception {
        enviar(ana, idDe(abrir(ana, bia)), "Oi");
        enviar(ana, idDe(abrir(ana, caio)), "Oi");

        listar(ana, "?q=souza").andExpect(jsonPath("$.itens[*].pessoa.username").value(contains("bia")));
        listar(ana, "?q=@CAIO").andExpect(jsonPath("$.itens[*].pessoa.username").value(contains("caio")));
        listar(ana, "?q=%25").andExpect(jsonPath("$.itens").isEmpty());
        listar(ana, "?q=_").andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void contaQueNaoApareceSaiDaListaDoContadorEDaConversa() throws Exception {
        String id = idDe(abrir(ana, bia));
        enviar(bia, id, "Oi");
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", bia.id());

        listar(ana, "").andExpect(jsonPath("$.itens").isEmpty());
        naoLidas(ana).andExpect(jsonPath("$.total").value(0));
        buscar(ana, id).andExpect(status().isNotFound());
        enviar(ana, id, "Oi?").andExpect(status().isNotFound());
    }

    /** As contas que não aparecem saem antes da paginação: a primeira página vem cheia. */
    @Test
    void aListaPaginaSemPaginaCurta() throws Exception {
        List<UUID> outros = new ArrayList<>();
        Instant inicio = Instant.parse("2026-11-01T12:00:00Z");
        for (int i = 0; i < 32; i++) {
            UUID outro = UUID.randomUUID();
            usuarioComId(outro, "pessoa" + i);
            conversaPorSql(ana.id(), outro, inicio.plusSeconds(i));
            outros.add(outro);
        }
        // A mais recente some: sobram 31.
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", outros.getLast());

        String corpo = listar(ana, "")
                .andExpect(jsonPath("$.itens", hasSize(30)))
                .andExpect(jsonPath("$.itens[0].pessoa.username").value("pessoa30"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String proximo = JsonPath.read(corpo, "$.proximo");

        listar(ana, "?cursor=" + proximo)
                .andExpect(jsonPath("$.itens[*].pessoa.username").value(contains("pessoa0")))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
        listar(ana, "?cursor=nao-e-um-cursor").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ mensagens

    @Test
    void asMensagensVemDaMaisNovaParaAMaisAntigaEmPaginas() throws Exception {
        String id = idDe(abrir(ana, bia));
        Instant inicio = Instant.parse("2026-11-01T12:00:00Z");
        for (int i = 0; i < 31; i++) {
            mensagemPorSql(UUID.fromString(id), i % 2 == 0 ? ana.id() : bia.id(), "Mensagem " + i,
                    inicio.plusSeconds(i));
        }

        String corpo = mensagens(bia, id, "")
                .andExpect(jsonPath("$.itens", hasSize(30)))
                .andExpect(jsonPath("$.itens[0].conteudo").value("Mensagem 30"))
                .andExpect(jsonPath("$.itens[0].remetenteId").value(ana.id().toString()))
                .andExpect(jsonPath("$.itens[0].conversaId").value(id))
                .andExpect(jsonPath("$.itens[0].apagada").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        mensagens(bia, id, "?cursor=" + JsonPath.read(corpo, "$.proximo"))
                .andExpect(jsonPath("$.itens[*].conteudo").value(contains("Mensagem 0")))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
    }

    @Test
    void oTextoPrecisaTerDeUmAteDoisMilCaracteres() throws Exception {
        String id = idDe(abrir(ana, bia));

        enviar(ana, id, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.conteudo").value("Escreva uma mensagem."));
        enviar(ana, id, "   ").andExpect(status().isBadRequest());
        enviar(ana, id, "a".repeat(2001)).andExpect(status().isBadRequest());
        comToken(post("/api/conversas/" + id + "/mensagens").contentType(MediaType.APPLICATION_JSON).content("{}"),
                ana).andExpect(status().isBadRequest());

        enviar(ana, id, "  " + "😀".repeat(2000) + "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conteudo").value("😀".repeat(2000)))
                .andExpect(jsonPath("$.remetenteId").value(ana.id().toString()));
    }

    @Test
    void quemNaoParticipaRecebe404EmTudo() throws Exception {
        String id = idDe(abrir(ana, bia));
        String mensagem = idDe(enviar(ana, id, "Só para a Bia"));

        buscar(caio, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Conversa não encontrada."));
        mensagens(caio, id, "").andExpect(status().isNotFound());
        enviar(caio, id, "Oi").andExpect(status().isNotFound());
        ler(caio, id, mensagem).andExpect(status().isNotFound());
        apagar(caio, id, mensagem).andExpect(status().isNotFound());
        buscar(caio, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void semLoginNaoVeNada() throws Exception {
        mvc.perform(get("/api/conversas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversas/nao-lidas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/conversas/com/" + bia.id())).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ leitura

    @Test
    void lerZeraANaoLidaEApareceComoVistoParaQuemMandou() throws Exception {
        String id = idDe(abrir(ana, bia));
        String primeira = idDe(enviar(ana, id, "Oi"));
        String segunda = idDe(enviar(ana, id, "Tudo bem?"));

        // Quem manda não fica com a própria mensagem como não lida.
        naoLidas(ana).andExpect(jsonPath("$.total").value(0));
        naoLidas(bia).andExpect(jsonPath("$.total").value(1));

        ler(bia, id, segunda).andExpect(status().isNoContent());

        naoLidas(bia).andExpect(jsonPath("$.total").value(0));
        listar(bia, "").andExpect(jsonPath("$.itens[0].naoLida").value(false));
        String lidaEm = JsonPath.read(buscar(ana, id).andReturn().getResponse().getContentAsString(),
                "$.outroLeuEm");
        assertThat(lidaEm).isNotNull();

        // Ler até uma mensagem mais antiga não volta a leitura.
        ler(bia, id, primeira).andExpect(status().isNoContent());
        buscar(ana, id).andExpect(jsonPath("$.outroLeuEm").value(lidaEm));

        // Uma mensagem de outra conversa não conta.
        String outra = idDe(abrir(caio, bia));
        ler(bia, outra, segunda).andExpect(status().isNoContent());
        ler(bia, id, "nao-e-um-id").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ apagar

    @Test
    void soQuemMandouApagaEOTextoSaiDoBanco() throws Exception {
        String id = idDe(abrir(ana, bia));
        String mensagem = idDe(enviar(ana, id, "Ops, mandei errado"));

        apagar(bia, id, mensagem)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Só quem mandou a mensagem pode apagá-la."));
        apagar(ana, id, mensagem).andExpect(status().isNoContent());
        apagar(ana, id, mensagem).andExpect(status().isNoContent());

        mensagens(bia, id, "")
                .andExpect(jsonPath("$.itens[0].apagada").value(true))
                .andExpect(jsonPath("$.itens[0].conteudo").value(nullValue()));
        listar(bia, "")
                .andExpect(jsonPath("$.itens[0].ultima.apagada").value(true))
                .andExpect(jsonPath("$.itens[0].ultima.texto").value(nullValue()))
                // Mensagem apagada não conta como não lida.
                .andExpect(jsonPath("$.itens[0].naoLida").value(false));
        assertThat(jdbc.queryForObject("select conteudo from mensagem where id = ?::uuid", String.class, mensagem))
                .isNull();

        String outra = idDe(abrir(ana, caio));
        apagar(ana, outra, mensagem).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ limite

    @Test
    void aTrigesimaPrimeiraMensagemNoMinutoEsbarraNoLimite() throws Exception {
        String id = idDe(abrir(ana, bia));
        for (int i = 0; i < 30; i++) {
            mensagemPorSql(UUID.fromString(id), ana.id(), "Mensagem " + i, null);
        }

        enviar(ana, id, "Mais uma")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Você mandou muitas mensagens em pouco tempo. Espere um pouco."));
        // O limite é de quem manda.
        enviar(bia, id, "Calma!").andExpect(status().isCreated());
    }

    @Test
    void aVigesimaPrimeiraConversaNovaNoDiaEsbarraNoLimite() throws Exception {
        for (int i = 0; i < 20; i++) {
            UUID outro = UUID.randomUUID();
            usuarioComId(outro, "pessoa" + i);
            conversaPorSql(ana.id(), outro, null);
        }
        String comBia = idDe(abrir(ana, bia));

        enviar(ana, comBia, "Oi").andExpect(status().isTooManyRequests());

        // Responder uma conversa que a outra pessoa começou não conta como conversa nova.
        enviar(bia, comBia, "Oi, Ana").andExpect(status().isCreated());
        enviar(ana, comBia, "Oi, Bia").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ ao vivo

    @Test
    void asMudancasChegamAoVivoParaAsDuasPessoas() throws Exception {
        String id = idDe(abrir(ana, bia));
        MvcResult daBia = abrirAoVivo(bia);
        MvcResult daAna = abrirAoVivo(ana);
        MvcResult doCaio = abrirAoVivo(caio);

        String mensagem = idDe(enviar(ana, id, "Olá, Bia!"));
        assertThat(conteudo(daBia))
                .contains("event:mensagem")
                .contains("\"conteudo\":\"Olá, Bia!\"")
                .contains("\"remetenteId\":\"" + ana.id() + "\"");
        assertThat(conteudo(daAna)).contains("event:mensagem");
        assertThat(conteudo(doCaio)).doesNotContain("event:mensagem");

        // Um envio recusado não sai.
        enviar(ana, id, "   ").andExpect(status().isBadRequest());
        assertThat(conteudo(daBia).split("event:mensagem\n", -1)).hasSize(2);

        ler(bia, id, mensagem);
        assertThat(conteudo(daAna)).contains("event:leitura").contains("\"leitorId\":\"" + bia.id() + "\"");

        apagar(ana, id, mensagem);
        assertThat(conteudo(daBia)).contains("event:mensagem-apagada").contains("\"mensagemId\":\"" + mensagem + "\"");
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

    private void usuarioComId(UUID id, String username) {
        jdbc.update("""
                insert into usuario (id, username, nome_completo, email, senha_hash, status)
                values (?, ?, ?, ?, 'hash', 'ATIVO')
                """, id, username, username, username + "@exemplo.com");
    }

    /** Uma conversa já com uma mensagem de {@code eu}, sem passar pelo limite de envio. */
    private void conversaPorSql(UUID eu, UUID outro, Instant quando) {
        UUID id = UUID.fromString(conversas.abrirCom(eu, outro).id().toString());
        mensagemPorSql(id, eu, "Oi", quando);
    }

    /** @param quando {@code null} para agora */
    private void mensagemPorSql(UUID conversaId, UUID remetente, String texto, Instant quando) {
        Timestamp em = Timestamp.from(quando != null ? quando : Instant.now());
        jdbc.update("insert into mensagem (conversa_id, remetente_id, conteudo, enviado_em) values (?, ?, ?, ?)",
                conversaId, remetente, texto, em);
        jdbc.update("""
                update conversa set ultima_mensagem_em = greatest(coalesce(ultima_mensagem_em, '-infinity'), ?)
                where id = ?
                """, em, conversaId);
    }

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private ResultActions abrir(Conta quem, Conta outro) throws Exception {
        return comToken(post("/api/conversas/com/" + outro.id()), quem);
    }

    private ResultActions buscar(Conta quem, String conversa) throws Exception {
        return comToken(get("/api/conversas/" + conversa), quem);
    }

    private ResultActions listar(Conta quem, String consulta) throws Exception {
        return comToken(get("/api/conversas" + consulta), quem);
    }

    private ResultActions naoLidas(Conta quem) throws Exception {
        return comToken(get("/api/conversas/nao-lidas"), quem);
    }

    private ResultActions mensagens(Conta quem, String conversa, String consulta) throws Exception {
        return comToken(get("/api/conversas/" + conversa + "/mensagens" + consulta), quem);
    }

    private ResultActions enviar(Conta quem, String conversa, String texto) throws Exception {
        String json = "{\"conteudo\": \"" + texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                + "\"}";
        return comToken(post("/api/conversas/" + conversa + "/mensagens")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(json), quem);
    }

    private ResultActions ler(Conta quem, String conversa, String mensagem) throws Exception {
        return comToken(post("/api/conversas/" + conversa + "/leitura")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ate\": \"" + mensagem + "\"}"), quem);
    }

    private ResultActions apagar(Conta quem, String conversa, String mensagem) throws Exception {
        return comToken(delete("/api/conversas/" + conversa + "/mensagens/" + mensagem), quem);
    }

    private MvcResult abrirAoVivo(Conta quem) throws Exception {
        return comToken(get("/api/aovivo").accept(MediaType.TEXT_EVENT_STREAM), quem)
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String conteudo(MvcResult conexao) throws Exception {
        return conexao.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Solta as ações juntas, cada uma na sua thread e na sua transação, e espera todas terminarem. */
    private static List<Object> aoMesmoTempo(Callable<?>... acoes) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(acoes.length)) {
            List<Future<?>> emAndamento = new ArrayList<>();
            for (Callable<?> acao : acoes) {
                emAndamento.add(threads.submit(() -> {
                    largada.await();
                    return acao.call();
                }));
            }
            largada.countDown();
            List<Object> respostas = new ArrayList<>();
            for (Future<?> acao : emAndamento) {
                respostas.add(acao.get(10, TimeUnit.SECONDS));
            }
            return respostas;
        }
    }
}
