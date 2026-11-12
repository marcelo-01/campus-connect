package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
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
class PerfilApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Conta ana;
    private Conta bia;

    @BeforeEach
    void criarContas() throws Exception {
        // O cascade leva junto tudo o que aponta para contas e bolhas: conexões, membros, publicações...
        jdbc.execute("truncate usuario, bolha cascade");
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");
    }

    // ------------------------------------------------------------------ leitura

    @Test
    void mostraOProprioPerfilSemEmail() throws Exception {
        comToken(get("/api/usuarios/ana.bc/perfil"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.bc"))
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Beatriz"))
                .andExpect(jsonPath("$.proprio").value(true))
                .andExpect(jsonPath("$.membroDesde").isNotEmpty())
                .andExpect(jsonPath("$.habilidades").isEmpty())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void perfilDeOutraPessoaNaoEhProprio() throws Exception {
        comToken(get("/api/usuarios/@ANA.BC/perfil"), bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.bc"))
                .andExpect(jsonPath("$.proprio").value(false));
    }

    @Test
    void contaSuspensaSoApareceParaAPropriaPessoa() throws Exception {
        jdbc.update("update usuario set status = 'SUSPENSO' where username = 'ana.bc'");

        comToken(get("/api/usuarios/ana.bc/perfil"), bia).andExpect(status().isNotFound());
        comToken(get("/api/usuarios/ana.bc/perfil"), ana).andExpect(status().isOk());
    }

    @Test
    void perfilExigeLoginEUsernameExistente() throws Exception {
        mvc.perform(get("/api/usuarios/ana.bc/perfil")).andExpect(status().isUnauthorized());
        comToken(get("/api/usuarios/ninguem/perfil"), ana).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ edição

    @Test
    void editaOPerfilNormalizandoLinkEHabilidades() throws Exception {
        atualizar(ana, """
                {"nomeCompleto": " Ana Beatriz Casiano ", "username": "@Ana.Casiano", "bio": " Estudante de ADS. ",
                 "link": "github.com/ana", "habilidades": [" React ", "react", "Figma", "", "SQL"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Beatriz Casiano"))
                .andExpect(jsonPath("$.username").value("ana.casiano"))
                .andExpect(jsonPath("$.bio").value("Estudante de ADS."))
                .andExpect(jsonPath("$.link").value("https://github.com/ana"))
                .andExpect(jsonPath("$.habilidades.length()").value(3))
                .andExpect(jsonPath("$.habilidades[0]").value("React"))
                .andExpect(jsonPath("$.habilidades[2]").value("SQL"));

        comToken(get("/api/usuarios/ana.casiano/perfil"), bia)
                .andExpect(jsonPath("$.habilidades[1]").value("Figma"))
                .andExpect(jsonPath("$.link").value("https://github.com/ana"));
        comToken(get("/api/usuarios/ana.bc/perfil"), bia).andExpect(status().isNotFound());
    }

    @Test
    void apagarBioELinkDeixaOsCamposVazios() throws Exception {
        atualizar(ana, perfil("ana.bc", "Uma bio", "ana.dev", "[\"Java\"]")).andExpect(status().isOk());

        atualizar(ana, perfil("ana.bc", "  ", "", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.link").doesNotExist())
                .andExpect(jsonPath("$.habilidades").isEmpty());
    }

    @Test
    void usernameDeOutraPessoaOuReservadoEhRecusado() throws Exception {
        atualizar(ana, perfil("BIA", null, null, "[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.username").value("Este nome de usuário já está em uso."));
        atualizar(ana, perfil("editar", null, null, "[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.username").value("Este nome de usuário não está disponível."));
    }

    @Test
    void manterOProprioUsernameNaoConflita() throws Exception {
        atualizar(ana, perfil("ana.bc", "Nova bio", null, "[]")).andExpect(status().isOk());
    }

    @Test
    void linkQueNaoEhEnderecoWebEhRecusado() throws Exception {
        atualizar(ana, perfil("ana.bc", null, "javascript:alert(1)", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.link").value(containsString("endereço web")));
    }

    @Test
    void bioLongaEHabilidadesDemaisSaoRecusadas() throws Exception {
        atualizar(ana, perfil("ana.bc", "x".repeat(201), null, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bio").exists());

        String dezesseis = "[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 16)
                .mapToObj(i -> "\"H" + i + "\"").toList()) + "]";
        atualizar(ana, perfil("ana.bc", null, null, dezesseis))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.habilidades").value("Use no máximo 15 habilidades."));
    }

    @Test
    void cadastroTambemNaoAceitaUsernameReservado() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "Eu", "username": "editar", "email": "editar@exemplo.com", "senha": "senha-forte-123"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros.username").exists());
    }

    // ------------------------------------------------------------------ imagens

    @Test
    void trocaAFotoEAImagemFicaPublica() throws Exception {
        String url = JsonPath.read(enviar("/api/usuarios/me/foto", png(1200, 800), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(matchesPattern("/api/arquivos/[0-9a-f]{32}\\.jpg")))
                .andReturn().getResponse().getContentAsString(), "$.fotoUrl");

        byte[] imagem = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(imagem));
        assertThat(lida.getWidth()).isEqualTo(512);
        assertThat(lida.getHeight()).isEqualTo(512);

        comToken(get("/api/usuarios/me"), ana).andExpect(jsonPath("$.fotoUrl").value(url));
    }

    @Test
    void trocarDeNovoApagaAImagemAnterior() throws Exception {
        String primeira = JsonPath.read(enviar("/api/usuarios/me/capa", png(1200, 400), ana)
                .andReturn().getResponse().getContentAsString(), "$.capaUrl");

        String segunda = JsonPath.read(enviar("/api/usuarios/me/capa", png(1200, 400), ana)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.capaUrl");

        assertThat(segunda).isNotEqualTo(primeira);
        mvc.perform(get(primeira)).andExpect(status().isNotFound());
        mvc.perform(get(segunda)).andExpect(status().isOk());
    }

    @Test
    void arquivoQueNaoEhImagemEhRecusado() throws Exception {
        enviar("/api/usuarios/me/foto", "<svg onload=alert(1)>".getBytes(), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.arquivo").exists());

        comToken(get("/api/usuarios/me"), ana).andExpect(jsonPath("$.fotoUrl").doesNotExist());
    }

    @Test
    void uploadExigeLogin() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("arquivo", png(10, 10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soEntregaArquivosComNomeGeradoPeloSistema() throws Exception {
        mvc.perform(get("/api/arquivos/application.yml")).andExpect(status().isNotFound());
        mvc.perform(get("/api/arquivos/" + "0".repeat(32) + ".jpg")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ bolhas e conexões

    @Test
    void contaBolhasEConexoesReais() throws Exception {
        comToken(get("/api/bolhas/usuario/" + ana.id()), bia).andExpect(jsonPath("$").isEmpty());
        comToken(get("/api/conexoes/usuario/" + ana.id() + "/total"), bia).andExpect(jsonPath("$.total").value(0));

        UUID bolha = jdbc.queryForObject(
                "insert into bolha (tipo, nome, slug) values ('COMUNIDADE', 'Cinema na sexta', 'cinema') returning id",
                UUID.class);
        jdbc.update("insert into membro_bolha (bolha_id, usuario_id, papel) values (?, ?, 'MEMBRO')", bolha, ana.id());
        jdbc.update("insert into membro_bolha (bolha_id, usuario_id, papel) values (?, ?, 'MEMBRO')", bolha, bia.id());
        jdbc.update("""
                insert into conexao (usuario_menor_id, usuario_maior_id, solicitante_id, status)
                values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid), ?, 'ACEITA')
                """, ana.id(), bia.id(), ana.id(), bia.id(), ana.id());

        comToken(get("/api/bolhas/usuario/" + ana.id()), bia)
                .andExpect(jsonPath("$[0].nome").value("Cinema na sexta"))
                .andExpect(jsonPath("$[0].tipo").value("COMUNIDADE"))
                .andExpect(jsonPath("$[0].totalMembros").value(2));
        comToken(get("/api/conexoes/usuario/" + bia.id() + "/total"), ana).andExpect(jsonPath("$.total").value(1));
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

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private ResultActions atualizar(Conta conta, String json) throws Exception {
        return comToken(put("/api/usuarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content(json), conta);
    }

    private ResultActions enviar(String caminho, byte[] conteudo, Conta conta) throws Exception {
        return comToken(multipart(caminho).file(new MockMultipartFile("arquivo", "foto.png", "image/png", conteudo)),
                conta);
    }

    private static String perfil(String username, String bio, String link, String habilidades) {
        return """
                {"nomeCompleto": "Ana Beatriz", "username": "%s", "bio": %s, "link": %s, "habilidades": %s}
                """.formatted(username, json(bio), json(link), habilidades);
    }

    private static String json(String texto) {
        return texto == null ? "null" : "\"" + texto + "\"";
    }

    private static byte[] png(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", saida);
        return saida.toByteArray();
    }
}
