package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

/** A busca de pessoas ({@code /api/usuarios/busca}) e a de posts ({@code /api/publicacoes/busca}). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BuscaApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Conta equipe;
    private Conta ana;
    private Conta bia;

    private UUID geral;
    private UUID curso;

    @BeforeEach
    void criarContasEBolhas() throws Exception {
        jdbc.execute("truncate usuario, bolha cascade");
        equipe = cadastrar("Equipe CampusConnect", "equipe");
        jdbc.update("update usuario set administrador = true where id = ?", equipe.id());
        ana = cadastrar("Ana Beatriz Souza", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");

        geral = criarBolha("IFBA", "GERAL", null);
        curso = criarBolha("ADS", "CURSO", criarBolha("Campus Eunápolis", "CAMPUS", geral));
    }

    // ------------------------------------------------------------------ pessoas

    @Test
    void achaPessoasPeloNomeSemAcentoEPeloUsername() throws Exception {
        cadastrar("João Vitor", "joaov");

        assertThat(pessoas("joao", ana)).containsExactly("joaov");
        assertThat(pessoas("JOÃO", ana)).containsExactly("joaov");
        assertThat(pessoas("@joaov", ana)).containsExactly("joaov");
        // Cada palavra pode estar no nome ou no username, em qualquer ordem e pela metade.
        assertThat(pessoas("souza ana", ana)).containsExactly("ana.bc");
        assertThat(pessoas("bea .bc", ana)).containsExactly("ana.bc");
        assertThat(pessoas("caio", ana)).isEmpty();
        // % e _ são texto, e não curingas.
        assertThat(pessoas("%", ana)).isEmpty();
        assertThat(pessoas("a_b", ana)).isEmpty();
    }

    @Test
    void oQueCasaMelhorVemPrimeiro() throws Exception {
        cadastrar("Fabiana Rocha", "fabi");
        cadastrar("Zuleica Bia", "zuleica");
        cadastrar("Mariana Lima", "mari");

        // Username igual, depois palavra do nome que começa com a busca, depois o resto.
        assertThat(pessoas("bia", ana)).containsExactly("bia", "zuleica", "fabi");
        // Nome que começa com a busca vem antes de quem só tem a busca no meio; no empate, pela ordem do nome.
        assertThat(pessoas("ana", ana)).containsExactly("ana.bc", "fabi", "mari");
        assertThat(pessoas("souza", ana)).containsExactly("ana.bc", "bia");
    }

    @Test
    void contasQueNaoAparecemParaOsOutrosFicamDeFora() throws Exception {
        Conta caio = cadastrar("Caio Souza", "caio");
        Conta davi = cadastrar("Davi Souza", "davi");
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", caio.id());
        jdbc.update("update usuario set email_verificado = false where id = ?", davi.id());

        assertThat(pessoas("souza", ana)).containsExactly("ana.bc", "bia");
    }

    @Test
    void pessoasVemEmPaginas() throws Exception {
        cadastrar("Carla Souza", "carla");

        comToken(get("/api/usuarios/busca").param("q", "souza").param("limite", "2"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].username").value("ana.bc"))
                .andExpect(jsonPath("$.itens[0].nomeCompleto").value("Ana Beatriz Souza"))
                .andExpect(jsonPath("$.itens[0].email").doesNotExist())
                .andExpect(jsonPath("$.proximo").value("2"));
        comToken(get("/api/usuarios/busca").param("q", "souza").param("limite", "2").param("cursor", "2"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].username").value(contains("carla")))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
        // Sem limite, a página tem 20; um limite acima disso vale 20.
        comToken(get("/api/usuarios/busca").param("q", "souza").param("limite", "500"), ana)
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
    }

    @Test
    void buscaDePessoasVaziaOuInvalida() throws Exception {
        comToken(get("/api/usuarios/busca"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
        assertThat(pessoas("  @  ", ana)).isEmpty();
        comToken(get("/api/usuarios/busca").param("q", "a".repeat(101)), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.q").value("A busca pode ter até 100 caracteres."));
        comToken(get("/api/usuarios/busca").param("q", "ana").param("cursor", "abc"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cursor").isNotEmpty());
        mvc.perform(get("/api/usuarios/busca").param("q", "ana")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ posts

    @Test
    void achaPostsPorPalavraSemAcentoComPluraisEVariacoes() throws Exception {
        entrar(ana, curso);
        String edital = idDe(publicar(ana, curso, "Saiu o edital das bolsas de estágio!"));
        String aula = idDe(publicar(ana, curso, "Hoje tem aula de Java no laboratório"));
        String pesquisa = idDe(publicar(ana, curso, "Bolsa de pesquisa aberta na biblioteca"));

        assertThat(posts("bolsa", ana)).containsExactly(pesquisa, edital);
        assertThat(posts("ESTÁGIOS", ana)).containsExactly(edital);
        assertThat(posts("estagio", ana)).containsExactly(edital);
        assertThat(posts("laboratorio java", ana)).containsExactly(aula);
        assertThat(posts("laboratório python", ana)).isEmpty();
        // Como numa busca da web: aspas pedem a frase, o sinal de menos tira uma palavra e "or" aceita qualquer uma.
        assertThat(posts("\"aula de java\"", ana)).containsExactly(aula);
        assertThat(posts("\"java de aula\"", ana)).isEmpty();
        assertThat(posts("bolsa -pesquisa", ana)).containsExactly(edital);
        assertThat(posts("java or biblioteca", ana)).containsExactly(pesquisa, aula);
        // Pedaço de palavra não casa.
        assertThat(posts("bibli", ana)).isEmpty();
    }

    @Test
    void buscaPostsDeTodaARedeComAsPermissoesDeQuemVe() throws Exception {
        entrar(ana, curso);
        String id = idDe(publicar(ana, curso, "Monitoria de cálculo na sexta"));

        // Bia não está em nenhuma bolha, mas acha o post e o vê como num feed.
        comToken(get("/api/publicacoes/busca").param("q", "calculo"), bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].publicacao.id").value(id))
                .andExpect(jsonPath("$.itens[0].publicacao.podeInteragir").value(false))
                .andExpect(jsonPath("$.itens[0].publicacao.podeEditar").value(false))
                .andExpect(jsonPath("$.itens[0].republicadaPor").value(nullValue()))
                .andExpect(jsonPath("$.topo").value(nullValue()))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
    }

    @Test
    void aBuscaAcompanhaAEdicaoEAExclusao() throws Exception {
        entrar(ana, curso);
        String id = idDe(publicar(ana, curso, "Vendo calculadora científica"));

        comToken(put("/api/publicacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"Vendo livro de física\"}"), ana).andExpect(status().isOk());
        assertThat(posts("calculadora", ana)).isEmpty();
        assertThat(posts("livros", ana)).containsExactly(id);

        comToken(delete("/api/publicacoes/" + id), ana).andExpect(status().isNoContent());
        assertThat(posts("livro", ana)).isEmpty();
    }

    @Test
    void contaSuspensaSaiDaBuscaDePosts() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        idDe(publicar(ana, curso, "Carona para o campus amanhã"));
        String daBia = idDe(publicar(bia, curso, "Carona para Porto Seguro"));
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", ana.id());

        assertThat(posts("carona", bia)).containsExactly(daBia);
    }

    @Test
    void postsVemEmPaginasDoMaisNovoParaOMaisAntigo() throws Exception {
        entrar(ana, curso);
        List<String> publicados = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            publicados.add(idDe(publicar(ana, curso, "Aviso número " + i)));
        }

        String corpo = comToken(get("/api/publicacoes/busca").param("q", "avisos"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(20)))
                .andReturn().getResponse().getContentAsString();
        List<String> primeira = JsonPath.read(corpo, "$.itens[*].publicacao.id");
        assertThat(primeira).containsExactlyElementsOf(publicados.reversed().subList(0, 20));

        String proximo = JsonPath.read(corpo, "$.proximo");
        comToken(get("/api/publicacoes/busca").param("q", "avisos").param("cursor", proximo), ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].publicacao.id").value(publicados.getFirst()))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
    }

    @Test
    void buscaDePostsVaziaOuInvalida() throws Exception {
        entrar(ana, curso);
        publicar(ana, curso, "De volta para casa").andExpect(status().isCreated());

        comToken(get("/api/publicacoes/busca"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
        // Só palavras que não contam, como "de" e "para": nada.
        assertThat(posts("de para", ana)).isEmpty();
        // Sinais soltos não quebram a busca.
        assertThat(posts("\" - or & | ! ( :*", ana)).isEmpty();
        comToken(get("/api/publicacoes/busca").param("q", "a".repeat(101)), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.q").value("A busca pode ter até 100 caracteres."));
        comToken(get("/api/publicacoes/busca").param("q", "casa").param("cursor", "nada"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cursor").isNotEmpty());
    }

    // ------------------------------------------------------------------ apoio

    private record Conta(UUID id, String token) {
    }

    private Conta cadastrar(String nome, String username) throws Exception {
        String corpo = ContasDeTeste.cadastrar(mvc, nome, username, username + "@exemplo.com");
        return new Conta(UUID.fromString(JsonPath.read(corpo, "$.usuario.id")), JsonPath.read(corpo, "$.accessToken"));
    }

    private UUID criarBolha(String nome, String tipo, UUID pai) throws Exception {
        String json = pai == null
                ? "{\"nome\": \"%s\", \"tipo\": \"%s\"}".formatted(nome, tipo)
                : "{\"nome\": \"%s\", \"tipo\": \"%s\", \"bolhaPaiId\": \"%s\"}".formatted(nome, tipo, pai);
        String corpo = comToken(post("/api/bolhas").contentType(MediaType.APPLICATION_JSON).content(json), equipe)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private void entrar(Conta conta, UUID bolha) throws Exception {
        comToken(post("/api/bolhas/" + bolha + "/participacao"), conta).andExpect(status().is2xxSuccessful());
    }

    private ResultActions publicar(Conta conta, UUID bolha, String texto) throws Exception {
        String json = "{\"bolhaIds\": [\"%s\"], \"conteudo\": \"%s\"}".formatted(bolha, texto);
        return comToken(multipart("/api/publicacoes").file(new MockMultipartFile("dados", "",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8))), conta);
    }

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    /** Os usernames achados, na ordem da resposta. */
    private List<String> pessoas(String termo, Conta conta) throws Exception {
        return JsonPath.read(comToken(get("/api/usuarios/busca").param("q", termo), conta)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.itens[*].username");
    }

    /** Os ids dos posts achados, na ordem da resposta. */
    private List<String> posts(String termo, Conta conta) throws Exception {
        return JsonPath.read(comToken(get("/api/publicacoes/busca").param("q", termo), conta)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.itens[*].publicacao.id");
    }

    private static String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }
}
