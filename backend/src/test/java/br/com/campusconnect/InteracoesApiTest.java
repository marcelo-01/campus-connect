package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InteracoesApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Conta equipe;
    private Conta ana;
    private Conta bia;
    private Conta caio;
    private UUID curso;
    private String post;

    @BeforeEach
    void criarCenario() throws Exception {
        // O cascade leva junto tudo o que aponta para contas e bolhas: conexões, membros, publicações...
        jdbc.execute("truncate usuario, bolha cascade");
        equipe = cadastrar("Equipe CampusConnect", "equipe");
        jdbc.update("update usuario set administrador = true where id = ?", equipe.id());
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");
        caio = cadastrar("Caio Lima", "caio"); // não entra em bolha nenhuma
        UUID geral = criarBolha("IFBA", "GERAL", null);
        curso = criarBolha("ADS", "CURSO", criarBolha("Campus Eunápolis", "CAMPUS", geral));
        entrar(ana, curso);
        entrar(bia, curso);
        post = publicar(ana, curso, "Quem vai na Semana Acadêmica?");
    }

    // ------------------------------------------------------------------ curtidas

    @Test
    void curtirEhIdempotenteEOContadorAcompanha() throws Exception {
        acao(post("/api/publicacoes/" + post + "/curtida"), bia).andExpect(status().isNoContent());
        acao(post("/api/publicacoes/" + post + "/curtida"), bia).andExpect(status().isNoContent());

        acao(get("/api/publicacoes/" + post), bia)
                .andExpect(jsonPath("$.curtidas").value(1))
                .andExpect(jsonPath("$.curtida").value(true));
        acao(get("/api/publicacoes/" + post), ana).andExpect(jsonPath("$.curtida").value(false));

        acao(delete("/api/publicacoes/" + post + "/curtida"), bia).andExpect(status().isNoContent());
        acao(delete("/api/publicacoes/" + post + "/curtida"), bia).andExpect(status().isNoContent());
        acao(get("/api/publicacoes/" + post), bia)
                .andExpect(jsonPath("$.curtidas").value(0))
                .andExpect(jsonPath("$.curtida").value(false));
    }

    @Test
    void quemNaoEhMembroNaoCurteNemRepublicaMasSalva() throws Exception {
        acao(post("/api/publicacoes/" + post + "/curtida"), caio).andExpect(status().isForbidden());
        acao(post("/api/publicacoes/" + post + "/republicacao"), caio).andExpect(status().isForbidden());
        acao(post("/api/publicacoes/" + post + "/salva"), caio).andExpect(status().isNoContent());

        acao(get("/api/publicacoes/" + post), caio).andExpect(jsonPath("$.salva").value(true));
    }

    @Test
    void publicacaoQueNaoExisteDa404() throws Exception {
        UUID nenhuma = UUID.randomUUID();
        acao(post("/api/publicacoes/" + nenhuma + "/curtida"), bia).andExpect(status().isNotFound());
        acao(post("/api/publicacoes/" + nenhuma + "/salva"), bia).andExpect(status().isNotFound());
        acao(get("/api/publicacoes/" + nenhuma + "/comentarios"), bia).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ salvas

    @Test
    void salvasAparecemSoParaQuemSalvou() throws Exception {
        String outro = publicar(ana, curso, "Outro post");
        acao(post("/api/publicacoes/" + post + "/salva"), bia).andExpect(status().isNoContent());
        acao(post("/api/publicacoes/" + outro + "/salva"), bia).andExpect(status().isNoContent());

        assertThat(ids(acao(get("/api/publicacoes/salvas"), bia))).containsExactly(outro, post);
        assertThat(ids(acao(get("/api/publicacoes/salvas"), ana))).isEmpty();

        acao(delete("/api/publicacoes/" + outro + "/salva"), bia).andExpect(status().isNoContent());
        assertThat(ids(acao(get("/api/publicacoes/salvas"), bia))).containsExactly(post);

        // Post excluído sai das salvas.
        acao(delete("/api/publicacoes/" + post), ana).andExpect(status().isNoContent());
        assertThat(ids(acao(get("/api/publicacoes/salvas"), bia))).isEmpty();
    }

    // ------------------------------------------------------------------ republicações

    @Test
    void republicarLevaOPostParaOPerfil() throws Exception {
        String daBia = publicar(bia, curso, "Post da Bia");
        acao(post("/api/publicacoes/" + post + "/republicacao"), bia).andExpect(status().isNoContent());

        String perfil = acao(get("/api/publicacoes/usuario/" + bia.id()), ana)
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].publicacao.id").value(post))
                .andExpect(jsonPath("$.itens[0].publicacao.autor.username").value("ana.bc"))
                .andExpect(jsonPath("$.itens[0].republicadaPor.username").value("bia"))
                .andExpect(jsonPath("$.itens[0].publicacao.republicacoes").value(1))
                .andExpect(jsonPath("$.itens[1].publicacao.id").value(daBia))
                .andExpect(jsonPath("$.itens[1].republicadaPor").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(perfil, "$.itens[0].publicacao.republicada")).isFalse(); // visto pela Ana
        acao(get("/api/publicacoes/" + post), bia).andExpect(jsonPath("$.republicada").value(true));

        // O perfil da Ana tem só o post dela.
        assertThat(ids(acao(get("/api/publicacoes/usuario/" + ana.id()), bia))).containsExactly(post);

        acao(delete("/api/publicacoes/" + post + "/republicacao"), bia).andExpect(status().isNoContent());
        assertThat(ids(acao(get("/api/publicacoes/usuario/" + bia.id()), ana))).containsExactly(daBia);
    }

    @Test
    void ninguemRepublicaOProprioPost() throws Exception {
        acao(post("/api/publicacoes/" + post + "/republicacao"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Você não pode republicar a própria publicação."));
    }

    // ------------------------------------------------------------------ comentários

    @Test
    void respostasFicamNumNivelSo() throws Exception {
        Conta duda = cadastrar("Duda Reis", "duda");
        entrar(duda, curso);
        String daBia = comentar(bia, post, "Eu vou!", null);
        String daAna = comentar(ana, post, "@bia que bom!", daBia);
        String daDuda = comentar(duda, post, "@ana.bc eu também", daAna); // responde a uma resposta

        acao(get("/api/publicacoes/" + post + "/comentarios"), caio)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(daBia))
                .andExpect(jsonPath("$[0].autor.username").value("bia"))
                .andExpect(jsonPath("$[0].respostas", hasSize(2)))
                .andExpect(jsonPath("$[0].respostas[0].id").value(daAna))
                .andExpect(jsonPath("$[0].respostas[1].id").value(daDuda))
                .andExpect(jsonPath("$[0].respostas[1].respostas", hasSize(0)));
        acao(get("/api/publicacoes/" + post), caio).andExpect(jsonPath("$.comentarios").value(3));
    }

    @Test
    void ordemRelevantesERecentes() throws Exception {
        String primeiro = comentar(bia, post, "Primeiro", null);
        String segundo = comentar(ana, post, "Segundo", null);
        String terceiro = comentar(bia, post, "Terceiro", null);
        acao(post("/api/publicacoes/comentarios/" + segundo + "/curtida"), bia).andExpect(status().isNoContent());

        assertThat(comentarios("relevantes")).containsExactly(segundo, primeiro, terceiro);
        assertThat(comentarios("recentes")).containsExactly(terceiro, segundo, primeiro);
        acao(get("/api/publicacoes/" + post + "/comentarios"), ana)
                .andExpect(jsonPath("$[0].curtidas").value(1))
                .andExpect(jsonPath("$[0].curtido").value(false));
    }

    @Test
    void comentarioExcluidoComRespostasViraRemovido() throws Exception {
        String daBia = comentar(bia, post, "Comentário da Bia", null);
        String resposta = comentar(ana, post, "Resposta da Ana", daBia);
        String sozinho = comentar(bia, post, "Outro da Bia", null);

        acao(delete("/api/publicacoes/comentarios/" + daBia), ana).andExpect(status().isForbidden());
        acao(delete("/api/publicacoes/comentarios/" + daBia), bia).andExpect(status().isNoContent());
        acao(delete("/api/publicacoes/comentarios/" + sozinho), equipe).andExpect(status().isNoContent());

        acao(get("/api/publicacoes/" + post + "/comentarios"), ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].removido").value(true))
                .andExpect(jsonPath("$[0].autor").value(nullValue()))
                .andExpect(jsonPath("$[0].conteudo").value(nullValue()))
                .andExpect(jsonPath("$[0].respostas[0].id").value(resposta));
        acao(get("/api/publicacoes/" + post), ana).andExpect(jsonPath("$.comentarios").value(1));
        acao(delete("/api/publicacoes/comentarios/" + daBia), bia).andExpect(status().isNotFound());
    }

    @Test
    void comentarioPrecisaDeMembroETexto() throws Exception {
        String outro = publicar(ana, curso, "Outro post");
        String deOutroPost = comentar(bia, outro, "Lá", null);

        acao(post("/api/publicacoes/" + post + "/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"Oi\"}"), caio).andExpect(status().isForbidden());
        acao(post("/api/publicacoes/" + post + "/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"  \"}"), bia)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.conteudo").exists());
        acao(post("/api/publicacoes/" + post + "/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"Aqui\", \"respondendoA\": \"%s\"}".formatted(deOutroPost)), bia)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.respondendoA").exists());
    }

    // ------------------------------------------------------------------ apoio

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

    private UUID criarBolha(String nome, String tipo, UUID pai) throws Exception {
        String json = pai == null
                ? "{\"nome\": \"%s\", \"tipo\": \"%s\"}".formatted(nome, tipo)
                : "{\"nome\": \"%s\", \"tipo\": \"%s\", \"bolhaPaiId\": \"%s\"}".formatted(nome, tipo, pai);
        String corpo = acao(post("/api/bolhas").contentType(MediaType.APPLICATION_JSON).content(json), equipe)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private void entrar(Conta conta, UUID bolha) throws Exception {
        acao(post("/api/bolhas/" + bolha + "/participacao"), conta).andExpect(status().is2xxSuccessful());
    }

    private String publicar(Conta conta, UUID bolha, String texto) throws Exception {
        String json = "{\"bolhaId\": \"%s\", \"conteudo\": \"%s\"}".formatted(bolha, texto);
        return JsonPath.read(acao(multipart("/api/publicacoes").file(new MockMultipartFile("dados", "",
                        MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8))), conta)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String comentar(Conta conta, String publicacao, String texto, String respondendoA) throws Exception {
        String json = respondendoA == null
                ? "{\"conteudo\": \"%s\"}".formatted(texto)
                : "{\"conteudo\": \"%s\", \"respondendoA\": \"%s\"}".formatted(texto, respondendoA);
        return JsonPath.read(acao(post("/api/publicacoes/" + publicacao + "/comentarios")
                        .contentType(MediaType.APPLICATION_JSON).content(json), conta)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private List<String> comentarios(String ordem) throws Exception {
        return JsonPath.read(acao(get("/api/publicacoes/" + post + "/comentarios").param("ordem", ordem), ana)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].id");
    }

    private ResultActions acao(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private static List<String> ids(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.itens[*].publicacao.id");
    }
}
