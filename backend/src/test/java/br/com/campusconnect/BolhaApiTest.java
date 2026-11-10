package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
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
class BolhaApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Administradora: a única que cria, edita e exclui. */
    private Conta equipe;
    private Conta ana;

    @BeforeEach
    void criarContas() throws Exception {
        jdbc.update("delete from conexao");
        jdbc.update("delete from bolha");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from usuario");
        equipe = cadastrar("Equipe CampusConnect", "equipe");
        jdbc.update("update usuario set administrador = true where id = ?", equipe.id());
        ana = cadastrar("Ana Beatriz", "ana.bc");
    }

    // ------------------------------------------------------------ permissão

    @Test
    void oUsuarioLogadoSabeSeEhAdministrador() throws Exception {
        comToken(get("/api/usuarios/me"), equipe).andExpect(jsonPath("$.administrador").value(true));
        comToken(get("/api/usuarios/me"), ana).andExpect(jsonPath("$.administrador").value(false));
    }

    @Test
    void contaSuspensaPerdeOsPoderesDeAdministrador() throws Exception {
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", equipe.id());

        criar(equipe, bolha("GERAL", "IFBA", null)).andExpect(status().isForbidden());
    }

    @Test
    void soAdministradorCriaEditaTrocaFotoEExclui() throws Exception {
        criar(ana, bolha("GERAL", "IFBA", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Só administradores podem criar, editar ou excluir bolhas."));

        UUID ifba = criarBolha("GERAL", "IFBA", null);
        comToken(put("/api/bolhas/" + ifba).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Outro nome"}
                """), ana).andExpect(status().isForbidden());
        enviarFoto(ifba, png(200, 200), ana).andExpect(status().isForbidden());
        comToken(delete("/api/bolhas/" + ifba), ana).andExpect(status().isForbidden());

        comToken(get("/api/bolhas/ifba"), ana).andExpect(jsonPath("$.nome").value("IFBA"));
    }

    @Test
    void semLoginNaoVeBolhas() throws Exception {
        mvc.perform(get("/api/bolhas")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ hierarquia

    @Test
    void criaAHierarquiaEListaCadaBolhaAntesDasDeDentro() throws Exception {
        criar(equipe, """
                {"tipo": "GERAL", "nome": "  IFBA  ", "sigla": "IFBA", "descricao": "A bolha-mãe", "endereco": " "}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/bolhas/ifba"))
                .andExpect(jsonPath("$.nome").value("IFBA"))
                .andExpect(jsonPath("$.slug").value("ifba"))
                .andExpect(jsonPath("$.sigla").value("IFBA"))
                .andExpect(jsonPath("$.descricao").value("A bolha-mãe"))
                .andExpect(jsonPath("$.endereco").doesNotExist())
                .andExpect(jsonPath("$.totalMembros").value(0))
                .andExpect(jsonPath("$.criadaEm").isNotEmpty());
        UUID ifba = idPeloSlug("ifba");
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);
        criarBolha("COMUNIDADE", "Robótica", campus);
        criarBolha("CURSO", "Análise e Desenvolvimento de Sistemas", campus);
        criarBolha("COMUNIDADE", "Cinema", null);

        // Irmãs: cursos antes de comunidades; no topo, gerais antes de comunidades.
        comToken(get("/api/bolhas"), ana)
                .andExpect(jsonPath("$[*].nome").value(contains("IFBA", "Campus Eunápolis",
                        "Análise e Desenvolvimento de Sistemas", "Robótica", "Cinema")))
                .andExpect(jsonPath("$[*].nivel").value(contains(0, 1, 2, 2, 0)))
                .andExpect(jsonPath("$[1].bolhaPaiId").value(ifba.toString()))
                .andExpect(jsonPath("$[2].slug").value("analise-e-desenvolvimento-de-sistemas"))
                .andExpect(jsonPath("$[2].tipo").value("CURSO"))
                .andExpect(jsonPath("$[0].membro").value(false));

        comToken(get("/api/bolhas/analise-e-desenvolvimento-de-sistemas"), ana)
                .andExpect(jsonPath("$.caminho[*].nome").value(contains("IFBA", "Campus Eunápolis")))
                .andExpect(jsonPath("$.filhas").isEmpty())
                .andExpect(jsonPath("$.membro").value(false));
        comToken(get("/api/bolhas/campus-eunapolis"), ana)
                .andExpect(jsonPath("$.filhas[*].nome").value(contains("Análise e Desenvolvimento de Sistemas",
                        "Robótica")));
    }

    @Test
    void recusaBolhaForaDoLugarNaHierarquia() throws Exception {
        UUID ifba = criarBolha("GERAL", "IFBA", null);
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);
        UUID cinema = criarBolha("COMUNIDADE", "Cinema", null);

        criar(equipe, bolha("GERAL", "Outra", ifba)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaPaiId").value("Uma bolha geral fica no topo: deixe sem bolha acima."));
        criar(equipe, bolha("CAMPUS", "Solto", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaPaiId").value("Um campus fica dentro de uma bolha geral."));
        criar(equipe, bolha("CURSO", "ADS", ifba)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaPaiId").value("Um curso fica dentro de um campus."));
        criar(equipe, bolha("COMUNIDADE", "Clube", cinema)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaPaiId").exists());
        criar(equipe, bolha("CURSO", "ADS", UUID.randomUUID())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaPaiId").value("A bolha acima não existe mais."));
        criar(equipe, bolha("PLANETA", "Marte", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.tipo").value("Escolha o tipo da bolha."));
        criar(equipe, """
                {"tipo": "GERAL", "nome": " ", "sigla": "ABCDEFGHIJK"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.sigla").value("Use no máximo 10 caracteres."));

        criar(equipe, bolha("COMUNIDADE", "Robótica", campus)).andExpect(status().isCreated());
    }

    @Test
    void geraSlugSemAcentoSemRepetirEForaDasRotasReservadas() throws Exception {
        criar(equipe, bolha("COMUNIDADE", "Café & Código", null)).andExpect(jsonPath("$.slug").value("cafe-codigo"));
        criar(equipe, bolha("COMUNIDADE", "Café & Código", null)).andExpect(jsonPath("$.slug").value("cafe-codigo-2"));
        criar(equipe, bolha("COMUNIDADE", "Nova", null)).andExpect(jsonPath("$.slug").value("nova-2"));
        criar(equipe, bolha("COMUNIDADE", "Usuário", null)).andExpect(jsonPath("$.slug").value("usuario-2"));
        criar(equipe, bolha("COMUNIDADE", "???", null)).andExpect(jsonPath("$.slug").value("bolha"));
    }

    @Test
    void editarMudaSoNomeSiglaDescricaoEEndereco() throws Exception {
        UUID ifba = criarBolha("GERAL", "IFBA", null);
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);

        comToken(put("/api/bolhas/" + campus).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Eunápolis", "sigla": " EUN ", "descricao": "Campus do sul da Bahia",
                 "endereco": "BR-367, Km 57", "tipo": "GERAL", "bolhaPaiId": null}
                """), equipe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Eunápolis"))
                .andExpect(jsonPath("$.sigla").value("EUN"))
                .andExpect(jsonPath("$.endereco").value("BR-367, Km 57"))
                .andExpect(jsonPath("$.slug").value("campus-eunapolis"))
                .andExpect(jsonPath("$.tipo").value("CAMPUS"))
                .andExpect(jsonPath("$.caminho[0].nome").value("IFBA"));

        comToken(put("/api/bolhas/" + campus).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Eunápolis", "sigla": "", "descricao": null}
                """), equipe)
                .andExpect(jsonPath("$.sigla").doesNotExist())
                .andExpect(jsonPath("$.descricao").doesNotExist());
    }

    // ------------------------------------------------------------ exclusão

    @Test
    void naoExcluiBolhaQueAindaTemSubBolhas() throws Exception {
        UUID ifba = criarBolha("GERAL", "IFBA", null);
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);

        comToken(delete("/api/bolhas/" + ifba), equipe)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Exclua as sub-bolhas antes de excluir esta bolha."));

        comToken(delete("/api/bolhas/" + campus), equipe).andExpect(status().isNoContent());
        comToken(delete("/api/bolhas/" + ifba), equipe).andExpect(status().isNoContent());
        comToken(get("/api/bolhas/ifba"), equipe).andExpect(status().isNotFound());
    }

    @Test
    void excluirTiraOsMembrosEApagaAFoto() throws Exception {
        UUID cinema = criarBolha("COMUNIDADE", "Cinema", null);
        comToken(post("/api/bolhas/" + cinema + "/participacao"), ana).andExpect(status().isNoContent());
        String foto = JsonPath.read(enviarFoto(cinema, png(300, 300), equipe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(matchesPattern("/api/arquivos/[0-9a-f]{32}\\.jpg")))
                .andReturn().getResponse().getContentAsString(), "$.fotoUrl");
        mvc.perform(get(foto)).andExpect(status().isOk());

        comToken(delete("/api/bolhas/" + cinema), equipe).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from membro_bolha", Long.class)).isZero();
        mvc.perform(get(foto)).andExpect(status().isNotFound());
    }

    @Test
    void trocarAFotoApagaAAnterior() throws Exception {
        UUID cinema = criarBolha("COMUNIDADE", "Cinema", null);
        String primeira = JsonPath.read(enviarFoto(cinema, png(300, 300), equipe)
                .andReturn().getResponse().getContentAsString(), "$.fotoUrl");

        enviarFoto(cinema, png(400, 400), equipe).andExpect(status().isOk());

        mvc.perform(get(primeira)).andExpect(status().isNotFound());
        enviarFoto(cinema, "não é imagem".getBytes(), equipe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.arquivo").exists());
    }

    // ------------------------------------------------------------ participação

    @Test
    void entrarNumCursoEntraTambemNasBolhasAcima() throws Exception {
        UUID ifba = criarBolha("GERAL", "IFBA", null);
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);
        UUID ads = criarBolha("CURSO", "ADS", campus);
        criarBolha("CURSO", "Engenharia", campus);

        comToken(post("/api/bolhas/" + ads + "/participacao"), ana).andExpect(status().isNoContent());
        comToken(post("/api/bolhas/" + ads + "/participacao"), ana).andExpect(status().isNoContent());

        comToken(get("/api/bolhas/usuario/" + ana.id()), ana)
                .andExpect(jsonPath("$[*].nome").value(contains("IFBA", "Campus Eunápolis", "ADS")))
                .andExpect(jsonPath("$[*].nivel").value(contains(0, 1, 2)))
                .andExpect(jsonPath("$[*].membro").value(contains(true, true, true)));
        comToken(get("/api/bolhas/ads"), ana)
                .andExpect(jsonPath("$.membro").value(true))
                .andExpect(jsonPath("$.totalMembros").value(1));
        comToken(get("/api/bolhas/ads"), equipe).andExpect(jsonPath("$.membro").value(false));
    }

    @Test
    void sairDoCampusSaiTambemDasBolhasDeDentro() throws Exception {
        UUID ifba = criarBolha("GERAL", "IFBA", null);
        UUID campus = criarBolha("CAMPUS", "Campus Eunápolis", ifba);
        UUID ads = criarBolha("CURSO", "ADS", campus);
        UUID robotica = criarBolha("COMUNIDADE", "Robótica", ads);
        comToken(post("/api/bolhas/" + robotica + "/participacao"), ana).andExpect(status().isNoContent());

        comToken(delete("/api/bolhas/" + campus + "/participacao"), ana).andExpect(status().isNoContent());

        comToken(get("/api/bolhas/usuario/" + ana.id()), ana)
                .andExpect(jsonPath("$[*].nome").value(contains("IFBA")));
    }

    @Test
    void listaOsMembrosSemEmailESemContasSuspensas() throws Exception {
        Conta bia = cadastrar("Bia Souza", "bia");
        UUID cinema = criarBolha("COMUNIDADE", "Cinema", null);
        comToken(post("/api/bolhas/" + cinema + "/participacao"), ana).andExpect(status().isNoContent());
        comToken(post("/api/bolhas/" + cinema + "/participacao"), bia).andExpect(status().isNoContent());
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", bia.id());

        comToken(get("/api/bolhas/" + cinema + "/membros"), equipe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("ana.bc"))
                .andExpect(jsonPath("$[0].nomeCompleto").value("Ana Beatriz"))
                .andExpect(jsonPath("$[0].papel").value("MEMBRO"))
                .andExpect(jsonPath("$[0].entrouEm").isNotEmpty())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void bolhaQueNaoExisteDa404() throws Exception {
        UUID qualquer = UUID.randomUUID();
        comToken(get("/api/bolhas/nao-existe"), ana).andExpect(status().isNotFound());
        comToken(get("/api/bolhas/" + qualquer + "/membros"), ana).andExpect(status().isNotFound());
        comToken(post("/api/bolhas/" + qualquer + "/participacao"), ana).andExpect(status().isNotFound());
        comToken(delete("/api/bolhas/" + qualquer + "/participacao"), ana).andExpect(status().isNotFound());
        comToken(delete("/api/bolhas/" + qualquer), equipe).andExpect(status().isNotFound());
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

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private ResultActions criar(Conta conta, String json) throws Exception {
        return comToken(post("/api/bolhas").contentType(MediaType.APPLICATION_JSON).content(json), conta);
    }

    private UUID criarBolha(String tipo, String nome, @Nullable UUID pai) throws Exception {
        String corpo = criar(equipe, bolha(tipo, nome, pai))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private UUID idPeloSlug(String slug) {
        return jdbc.queryForObject("select id from bolha where slug = ?", UUID.class, slug);
    }

    private ResultActions enviarFoto(UUID bolha, byte[] conteudo, Conta conta) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.png", "image/png", conteudo);
        return comToken(multipart("/api/bolhas/" + bolha + "/foto").file(arquivo), conta);
    }

    private static String bolha(String tipo, String nome, @Nullable UUID pai) {
        return """
                {"tipo": "%s", "nome": "%s", "bolhaPaiId": %s}
                """.formatted(tipo, nome, pai == null ? "null" : "\"" + pai + "\"");
    }

    private static byte[] png(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", saida);
        return saida.toByteArray();
    }
}
