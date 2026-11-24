package br.com.campusconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * O que os testes do marketplace têm em comum: as contas, a árvore de bolhas e os atalhos para anunciar.
 *
 * <p>As categorias vêm da migration e não são apagadas entre os testes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiMarketplaceTeste {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected Conta equipe;
    protected Conta ana;
    protected Conta bia;
    /** Uma conta que não está em nenhuma bolha. */
    protected Conta caio;

    protected UUID geral;
    protected UUID campus;
    protected UUID curso;
    protected UUID comunidade;

    @BeforeEach
    void criarContasEBolhas() throws Exception {
        // O cascade leva junto tudo o que aponta para contas e bolhas, inclusive os anúncios.
        jdbc.execute("truncate usuario, bolha cascade");
        equipe = cadastrar("Equipe CampusConnect", "equipe");
        jdbc.update("update usuario set administrador = true where id = ?", equipe.id());
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");
        caio = cadastrar("Caio Lima", "caio");

        geral = criarBolha("IFBA", "GERAL", null);
        campus = criarBolha("Campus Eunápolis", "CAMPUS", geral);
        curso = criarBolha("ADS", "CURSO", campus);
        comunidade = criarBolha("Clube de Xadrez", "COMUNIDADE", curso);
    }

    protected record Conta(UUID id, String token) {
    }

    /**
     * Os campos de um anúncio, já válidos: um serviço de tecnologia a R$ 120. Cada teste troca o que
     * precisa com os métodos {@code com…}.
     */
    protected static Dados servico(UUID... bolhas) {
        return new Dados().com("tipo", "SERVICO").com("categoriaId", null).categoria("tecnologia")
                .com("titulo", "Site em React").com("descricao", "Landing page responsiva")
                .com("modalidadePreco", "FIXO").com("preco", 120).com("bolhaIds", List.of(bolhas));
    }

    protected static Dados produto(UUID... bolhas) {
        return servico(bolhas).com("tipo", "PRODUTO").categoria("livros-apostilas").com("titulo", "Apostila de Cálculo")
                .com("modalidadePreco", "GRATUITO").com("preco", null);
    }

    /** O JSON da parte {@code dados}, montado à mão para os testes controlarem cada campo. */
    protected static final class Dados {

        private final Map<String, Object> campos = new LinkedHashMap<>();
        private String categoria;

        Dados com(String campo, Object valor) {
            campos.put(campo, valor);
            return this;
        }

        /** A categoria pelo slug; o id é buscado na hora de enviar. */
        Dados categoria(String slug) {
            this.categoria = slug;
            return this;
        }

        String json(JdbcTemplate jdbc) {
            if (categoria != null) {
                campos.put("categoriaId",
                        jdbc.queryForObject("select id from categoria_anuncio where slug = ?", UUID.class, categoria));
            }
            return valor(campos);
        }

        private static String valor(Object valor) {
            if (valor == null) {
                return "null";
            }
            if (valor instanceof Number || valor instanceof Boolean) {
                return valor.toString();
            }
            if (valor instanceof Map<?, ?> mapa) {
                return mapa.entrySet().stream().map(campo -> "\"" + campo.getKey() + "\": " + valor(campo.getValue()))
                        .collect(Collectors.joining(", ", "{", "}"));
            }
            if (valor instanceof List<?> lista) {
                return lista.stream().map(Dados::valor).collect(Collectors.joining(", ", "[", "]"));
            }
            return "\"" + valor.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    // ------------------------------------------------------------------ requisições

    protected Conta cadastrar(String nome, String username) throws Exception {
        String corpo = ContasDeTeste.cadastrar(mvc, nome, username, username + "@exemplo.com");
        return new Conta(UUID.fromString(JsonPath.read(corpo, "$.usuario.id")), JsonPath.read(corpo, "$.accessToken"));
    }

    protected UUID criarBolha(String nome, String tipo, UUID pai) throws Exception {
        String json = pai == null
                ? "{\"nome\": \"%s\", \"tipo\": \"%s\"}".formatted(nome, tipo)
                : "{\"nome\": \"%s\", \"tipo\": \"%s\", \"bolhaPaiId\": \"%s\"}".formatted(nome, tipo, pai);
        String corpo = comToken(post("/api/bolhas").contentType(MediaType.APPLICATION_JSON).content(json), equipe)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    /** Entrar numa bolha também entra nas bolhas acima dela. */
    protected void entrar(Conta conta, UUID bolha) throws Exception {
        comToken(post("/api/bolhas/" + bolha + "/participacao"), conta).andExpect(status().is2xxSuccessful());
    }

    protected ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta)
            throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    /** Anuncia, com as fotos dadas, e devolve a resposta sem conferir o status. */
    protected ResultActions anunciar(Conta conta, Dados dados, MockMultipartFile... fotos) throws Exception {
        MockMultipartHttpServletRequestBuilder requisicao = multipart("/api/anuncios").file(parteDados(dados));
        for (MockMultipartFile foto : fotos) {
            requisicao.file(foto);
        }
        return comToken(requisicao, conta);
    }

    /** Anuncia e devolve o id do anúncio criado. */
    protected String anunciado(Conta conta, Dados dados, MockMultipartFile... fotos) throws Exception {
        return JsonPath.read(anunciar(conta, dados, fotos).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected ResultActions editar(Conta conta, String id, Dados dados, MockMultipartFile... fotos) throws Exception {
        MockMultipartHttpServletRequestBuilder requisicao = multipart(HttpMethod.PUT, "/api/anuncios/" + id)
                .file(parteDados(dados));
        for (MockMultipartFile foto : fotos) {
            requisicao.file(foto);
        }
        return comToken(requisicao, conta);
    }

    private MockMultipartFile parteDados(Dados dados) {
        return new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                dados.json(jdbc).getBytes(StandardCharsets.UTF_8));
    }

    protected static MockMultipartFile foto(String nome) {
        return new MockMultipartFile("imagens", nome, "image/png", ServidorPaginas.png(640, 480));
    }

    protected static MockMultipartFile[] fotos(int quantas) {
        List<MockMultipartFile> fotos = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            fotos.add(foto("f" + i + ".png"));
        }
        return fotos.toArray(MockMultipartFile[]::new);
    }

    protected static String corpo(ResultActions resposta) throws Exception {
        return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Os ids dos anúncios de uma página da vitrine ou do perfil, na ordem. */
    protected static List<String> ids(ResultActions resposta) throws Exception {
        return JsonPath.read(corpo(resposta.andExpect(status().isOk())), "$.itens[*].id");
    }
}
