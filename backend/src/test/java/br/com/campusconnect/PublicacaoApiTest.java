package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublicacaoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private ServidorPaginas paginas;

    private Conta equipe;
    private Conta ana;
    private Conta bia;

    private UUID geral;
    private UUID campus;
    private UUID curso;
    private UUID comunidade;

    @BeforeEach
    void criarContasEBolhas() throws Exception {
        // O cascade leva junto tudo o que aponta para contas e bolhas: conexões, membros, publicações...
        jdbc.execute("truncate usuario, bolha cascade");
        paginas = new ServidorPaginas();
        equipe = cadastrar("Equipe CampusConnect", "equipe");
        jdbc.update("update usuario set administrador = true where id = ?", equipe.id());
        ana = cadastrar("Ana Beatriz", "ana.bc");
        bia = cadastrar("Bia Souza", "bia");

        geral = criarBolha("IFBA", "GERAL", null);
        campus = criarBolha("Campus Eunápolis", "CAMPUS", geral);
        curso = criarBolha("ADS", "CURSO", campus);
        comunidade = criarBolha("Clube de Xadrez", "COMUNIDADE", curso);
    }

    @AfterEach
    void desligarServidor() {
        paginas.close();
    }

    // ------------------------------------------------------------------ publicar

    @Test
    void publicaTextoEOPostApareceNoInicio() throws Exception {
        entrar(ana, curso);

        String corpo = publicar(ana, curso, "Alguém para estudar Java hoje?", null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/publicacoes/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.conteudo").value("Alguém para estudar Java hoje?"))
                .andExpect(jsonPath("$.autor.username").value("ana.bc"))
                .andExpect(jsonPath("$.bolha.id").value(curso.toString()))
                .andExpect(jsonPath("$.midias", hasSize(0)))
                .andExpect(jsonPath("$.link").value(nullValue()))
                .andExpect(jsonPath("$.editadoEm").value(nullValue()))
                .andExpect(jsonPath("$.curtidas").value(0))
                .andExpect(jsonPath("$.podeInteragir").value(true))
                .andExpect(jsonPath("$.podeEditar").value(true))
                .andExpect(jsonPath("$.podeExcluir").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");

        comToken(get("/api/publicacoes/feed"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].publicacao.id").value(id))
                .andExpect(jsonPath("$.itens[0].republicadaPor").value(nullValue()))
                .andExpect(jsonPath("$.proximo").value(nullValue()));
    }

    @Test
    void quemNaoEhMembroNaoPublicaMasLe() throws Exception {
        entrar(ana, curso);
        publicar(bia, curso, "Oi!", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Entre na bolha para interagir com as publicações dela."));

        String id = idDe(publicar(ana, curso, "Aberto para quem passar", null));

        comToken(get("/api/publicacoes/bolha/" + curso), bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].publicacao.podeInteragir").value(false))
                .andExpect(jsonPath("$.itens[0].publicacao.podeEditar").value(false))
                .andExpect(jsonPath("$.itens[0].publicacao.podeExcluir").value(false));
        comToken(get("/api/publicacoes/" + id), bia).andExpect(status().isOk());
        // Bia não está em nenhuma bolha: o Início dela fica vazio.
        comToken(get("/api/publicacoes/feed"), bia).andExpect(jsonPath("$.itens", hasSize(0)));
    }

    @Test
    void bolhaQueNaoExisteDaErro() throws Exception {
        publicar(ana, UUID.randomUUID(), "Para onde?", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaId").exists());
        comToken(get("/api/publicacoes/bolha/" + UUID.randomUUID()), ana).andExpect(status().isNotFound());
        comToken(get("/api/publicacoes/" + UUID.randomUUID()), ana).andExpect(status().isNotFound());
    }

    @Test
    void publicaImagensVideoELink() throws Exception {
        entrar(ana, curso);

        comToken(publicacao(curso, "Fotos do evento", null)
                        .file(imagem("a.png")).file(imagem("b.png")), ana)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.midias", hasSize(2)))
                .andExpect(jsonPath("$.midias[0].tipo").value("IMAGEM"))
                .andExpect(jsonPath("$.midias[0].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.midias[1].url").value(matchesPattern("/api/arquivos/[0-9a-f]{32}\\.jpg")));

        String video = JsonPath.read(comToken(publicacao(curso, "", null).file(video("aula.mp4", mp4())), ana)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conteudo").value(""))
                .andExpect(jsonPath("$.midias", hasSize(1)))
                .andExpect(jsonPath("$.midias[0].tipo").value("VIDEO"))
                .andExpect(jsonPath("$.midias[0].mimeType").value("video/mp4"))
                .andReturn().getResponse().getContentAsString(), "$.midias[0].url");
        mvc.perform(get(video)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "video/mp4"));

        // O link para uma página que não existe sai sem prévia: só o endereço.
        String semPrevia = paginas.url("/nao-existe");
        publicar(ana, curso, "Olha isso", semPrevia)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link.url").value(semPrevia))
                .andExpect(jsonPath("$.link.titulo").value(nullValue()))
                .andExpect(jsonPath("$.link.imagemUrl").value(nullValue()));
    }

    @Test
    void linkGanhaPreviaComCopiaDaImagem() throws Exception {
        entrar(ana, curso);
        paginas.html("/evento", """
                        <html><head><meta property="og:title" content="Hackathon do Campus">
                        <meta property="og:description" content="48 horas de código.">
                        <meta property="og:image" content="/capa.png"></head></html>
                        """)
                .responder("/capa.png", 200, "image/png", ServidorPaginas.png(800, 400));

        String corpo = publicar(ana, curso, "Inscrições abertas!", paginas.url("/evento"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link.titulo").value("Hackathon do Campus"))
                .andExpect(jsonPath("$.link.descricao").value("48 horas de código."))
                .andExpect(jsonPath("$.link.site").value("127.0.0.1"))
                .andExpect(jsonPath("$.link.imagemUrl").value(matchesPattern("/api/arquivos/[0-9a-f]{32}\\.jpg")))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get(JsonPath.<String>read(corpo, "$.link.imagemUrl"))).andExpect(status().isOk());
    }

    @Test
    void conteudoInvalidoEhRecusado() throws Exception {
        entrar(ana, curso);

        publicar(ana, curso, "   ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.conteudo").value("Escreva algo ou anexe uma mídia."));
        publicar(ana, curso, "x".repeat(3001), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.conteudo").exists());
        publicar(ana, curso, "Clique", "javascript:alert(1)")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.link").exists());

        MockMultipartHttpServletRequestBuilder cincoImagens = publicacao(curso, "Muitas fotos", null);
        for (int i = 0; i < 5; i++) {
            cincoImagens.file(imagem(i + ".png"));
        }
        comToken(cincoImagens, ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.imagens").value("Anexe até 4 imagens."));
        comToken(publicacao(curso, "Os dois", null).file(imagem("a.png")).file(video("v.mp4", mp4())), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.video").exists());
        comToken(publicacao(curso, "Vídeo falso", null)
                        .file(video("v.mp4", "<html>não sou vídeo</html>".getBytes(StandardCharsets.UTF_8))), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.arquivo").value("Envie um vídeo MP4 ou WebM."));
        comToken(publicacao(curso, "Imagem falsa", null)
                        .file(new MockMultipartFile("imagens", "x.png", "image/png", "oi".getBytes(StandardCharsets.UTF_8))), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.arquivo").exists());

        assertThat(jdbc.queryForObject("select count(*) from publicacao", Long.class)).isZero();
    }

    // ------------------------------------------------------------------ hierarquia (RF08)

    @Test
    void oPostDesceParaAsBolhasDeDentroMasNaoSobe() throws Exception {
        entrar(ana, comunidade); // entra também no curso, no campus e na geral
        String doGeral = idDe(publicar(ana, geral, "Comunicado para todos", null));
        String doCampus = idDe(publicar(ana, campus, "Aviso do campus", null));
        String doCurso = idDe(publicar(ana, curso, "Prova de ADS na sexta", null));
        String daComunidade = idDe(publicar(ana, comunidade, "Torneio de xadrez", null));

        assertThat(ids(comToken(get("/api/publicacoes/bolha/" + geral), bia))).containsExactly(doGeral);
        assertThat(ids(comToken(get("/api/publicacoes/bolha/" + campus), bia))).containsExactly(doCampus, doGeral);
        assertThat(ids(comToken(get("/api/publicacoes/bolha/" + curso), bia)))
                .containsExactly(doCurso, doCampus, doGeral);
        assertThat(ids(comToken(get("/api/publicacoes/bolha/" + comunidade), bia)))
                .containsExactly(daComunidade, doCurso, doCampus, doGeral);

        // A etiqueta mostra a bolha de origem, mesmo vista de dentro do curso.
        comToken(get("/api/publicacoes/bolha/" + curso), bia)
                .andExpect(jsonPath("$.itens[2].publicacao.bolha.id").value(geral.toString()));
    }

    @Test
    void oInicioMostraAsMinhasBolhasEOFiltroSoAsDaBolhaEscolhida() throws Exception {
        entrar(ana, comunidade);
        entrar(bia, curso); // está no curso, no campus e na geral, mas não na comunidade
        String doGeral = idDe(publicar(ana, geral, "Comunicado", null));
        String doCurso = idDe(publicar(ana, curso, "Prova", null));
        String daComunidade = idDe(publicar(ana, comunidade, "Torneio", null));

        assertThat(ids(comToken(get("/api/publicacoes/feed"), bia))).containsExactly(doCurso, doGeral);
        assertThat(ids(comToken(get("/api/publicacoes/feed"), ana))).containsExactly(daComunidade, doCurso, doGeral);

        assertThat(ids(comToken(get("/api/publicacoes/feed").param("bolhaId", curso.toString()), bia)))
                .containsExactly(doCurso);
        assertThat(ids(comToken(get("/api/publicacoes/feed").param("bolhaId", geral.toString()), bia)))
                .containsExactly(doGeral);
    }

    // ------------------------------------------------------------------ paginação e chips

    @Test
    void oFeedVemEmPaginasSemRepetir() throws Exception {
        entrar(ana, curso);
        List<String> publicadas = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            publicadas.addFirst(idDe(publicar(ana, curso, "Post " + i, null)));
        }

        String primeira = comToken(get("/api/publicacoes/feed"), ana)
                .andExpect(jsonPath("$.itens", hasSize(20)))
                .andExpect(jsonPath("$.proximo").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String segunda = comToken(get("/api/publicacoes/feed").param("cursor", JsonPath.<String>read(primeira, "$.proximo")), ana)
                .andExpect(jsonPath("$.itens", hasSize(5)))
                .andExpect(jsonPath("$.proximo").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        List<String> todas = new ArrayList<>(JsonPath.<List<String>>read(primeira, "$.itens[*].publicacao.id"));
        todas.addAll(JsonPath.read(segunda, "$.itens[*].publicacao.id"));
        assertThat(todas).containsExactlyElementsOf(publicadas);

        comToken(get("/api/publicacoes/feed").param("cursor", "lixo!"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cursor").exists());
    }

    @Test
    void emAltaOrdenaPorCurtidasEComMidiaFiltra() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        Conta caio = cadastrar("Caio Lima", "caio");
        entrar(caio, curso);
        String umaCurtida = idDe(publicar(ana, curso, "Uma curtida", null));
        String comFoto = idDe(comToken(publicacao(curso, "Com foto", null).file(imagem("f.png")), ana));
        String duasCurtidas = idDe(publicar(ana, curso, "Duas curtidas", null));
        curtir(bia, umaCurtida);
        curtir(bia, duasCurtidas);
        curtir(caio, duasCurtidas);

        assertThat(ids(comToken(get("/api/publicacoes/feed").param("ordem", "alta"), ana)))
                .containsExactly(duasCurtidas, umaCurtida, comFoto);
        assertThat(ids(comToken(get("/api/publicacoes/bolha/" + curso).param("ordem", "midia"), ana)))
                .containsExactly(comFoto);
        comToken(get("/api/publicacoes/feed").param("ordem", "aleatorio"), ana).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ aviso de posts novos

    @Test
    void avisaQuantosPostsOsOutrosPublicaramDepoisQueOFeedAbriu() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        Conta caio = cadastrar("Caio Lima", "caio");
        entrar(caio, curso);
        idDe(publicar(bia, curso, "Já estava aqui", null));
        String topo = topo(comToken(get("/api/publicacoes/feed"), ana));

        novas(get("/api/publicacoes/feed/novas"), topo, ana)
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.mais").value(false))
                .andExpect(jsonPath("$.autores", hasSize(0)));

        // O post da própria Ana entra no feed dela na hora em que ela publica; o aviso não conta.
        idDe(publicar(ana, curso, "Meu post", null));
        String apagado = idDe(publicar(bia, curso, "Vou apagar", null));
        idDe(publicar(bia, curso, "Novo da Bia", null));
        idDe(publicar(caio, curso, "Novo do Caio", null));
        comToken(delete("/api/publicacoes/" + apagado), bia).andExpect(status().isNoContent());

        ResultActions aviso = novas(get("/api/publicacoes/feed/novas"), topo, ana)
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.mais").value(false));
        // Quem publicou por último vem primeiro.
        assertThat(autores(aviso)).containsExactly("caio", "bia");

        // Como no feed, o post de uma conta suspensa some do aviso.
        jdbc.update("update usuario set status = 'SUSPENSO' where id = ?", caio.id());
        aviso = novas(get("/api/publicacoes/feed/novas"), topo, ana).andExpect(jsonPath("$.total").value(1));
        assertThat(autores(aviso)).containsExactly("bia");
    }

    @Test
    void oAvisoSegueAHierarquiaOFiltroEOChipDoFeed() throws Exception {
        entrar(ana, comunidade); // entra também no curso, no campus e na geral
        entrar(bia, curso); // está no curso, no campus e na geral, mas não na comunidade
        // Os feeds da Bia abrem vazios: tudo o que chegar depois é novo.
        String inicio = topo(comToken(get("/api/publicacoes/feed"), bia));
        String filtrado = topo(comToken(get("/api/publicacoes/feed").param("bolhaId", curso.toString()), bia));
        String daBolha = topo(comToken(get("/api/publicacoes/bolha/" + curso), bia));
        String comMidia = topo(comToken(get("/api/publicacoes/bolha/" + curso).param("ordem", "midia"), bia));

        idDe(publicar(ana, geral, "Desce para o curso", null));
        idDe(publicar(ana, comunidade, "Não sobe para o curso", null));
        idDe(comToken(publicacao(curso, "Com foto", null).file(imagem("f.png")), ana));

        novas(get("/api/publicacoes/feed/novas"), inicio, bia).andExpect(jsonPath("$.total").value(2));
        // Filtrado pelo curso, o Início só conta o que foi feito no curso.
        novas(get("/api/publicacoes/feed/novas").param("bolhaId", curso.toString()), filtrado, bia)
                .andExpect(jsonPath("$.total").value(1));
        // A bolha conta o que desce da geral, mas não o que foi feito na comunidade, que fica dentro dela.
        novas(get("/api/publicacoes/bolha/" + curso + "/novas"), daBolha, bia).andExpect(jsonPath("$.total").value(2));
        novas(get("/api/publicacoes/bolha/" + curso + "/novas").param("ordem", "midia"), comMidia, bia)
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void oAvisoContaAteVinteESoAPrimeiraPaginaPorDataTrazOTopo() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        String vazio = topo(comToken(get("/api/publicacoes/feed"), ana));
        for (int i = 1; i <= 21; i++) {
            idDe(publicar(bia, curso, "Post " + i, null));
        }

        ResultActions aviso = novas(get("/api/publicacoes/feed/novas"), vazio, ana)
                .andExpect(jsonPath("$.total").value(20))
                .andExpect(jsonPath("$.mais").value(true));
        assertThat(autores(aviso)).containsExactly("bia");

        // Depois de ver os posts novos, a marca é outra e nada mais é novo.
        String primeira = comToken(get("/api/publicacoes/feed"), ana)
                .andExpect(jsonPath("$.topo").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        novas(get("/api/publicacoes/feed/novas"), JsonPath.read(primeira, "$.topo"), ana)
                .andExpect(jsonPath("$.total").value(0));

        comToken(get("/api/publicacoes/feed").param("cursor", JsonPath.<String>read(primeira, "$.proximo")), ana)
                .andExpect(jsonPath("$.topo").value(nullValue()));
        comToken(get("/api/publicacoes/feed").param("ordem", "alta"), ana).andExpect(jsonPath("$.topo").value(nullValue()));
        comToken(get("/api/publicacoes/usuario/" + bia.id()), ana).andExpect(jsonPath("$.topo").value(nullValue()));
        comToken(get("/api/publicacoes/salvas"), ana).andExpect(jsonPath("$.topo").value(nullValue()));
    }

    @Test
    void oAvisoRecusaMarcaInvalidaEmAltaEBolhaQueNaoExiste() throws Exception {
        entrar(ana, curso);
        String topo = topo(comToken(get("/api/publicacoes/feed"), ana));

        comToken(get("/api/publicacoes/feed/novas"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.desde").exists());
        comToken(get("/api/publicacoes/feed/novas").param("desde", "lixo!"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.desde").exists());
        comToken(get("/api/publicacoes/feed/novas").param("desde", topo).param("ordem", "alta"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.ordem").exists());
        comToken(get("/api/publicacoes/bolha/" + UUID.randomUUID() + "/novas").param("desde", topo), ana)
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ editar e excluir

    @Test
    void soQuemEscreveuEditaEOPostFicaMarcado() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        String id = idDe(publicar(ana, curso, "Texto com erro de digitaçao", null));

        editar(bia, id, "Invadido").andExpect(status().isForbidden());
        editar(ana, id, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.conteudo").exists());
        editar(ana, id, "Texto com erro de digitação")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").value("Texto com erro de digitação"))
                .andExpect(jsonPath("$.editadoEm").value(notNullValue()));
    }

    @Test
    void excluiQuemEscreveuOuUmAdministradorEOsArquivosSaem() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        String corpo = comToken(publicacao(curso, "Com foto", null).file(imagem("f.png")), ana)
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");
        String foto = JsonPath.read(corpo, "$.midias[0].url");
        String outro = idDe(publicar(ana, curso, "Outro", null));

        comToken(delete("/api/publicacoes/" + id), bia).andExpect(status().isForbidden());
        comToken(delete("/api/publicacoes/" + id), ana).andExpect(status().isNoContent());
        comToken(delete("/api/publicacoes/" + outro), equipe).andExpect(status().isNoContent());

        comToken(get("/api/publicacoes/" + id), ana).andExpect(status().isNotFound());
        comToken(delete("/api/publicacoes/" + id), ana).andExpect(status().isNotFound());
        assertThat(ids(comToken(get("/api/publicacoes/feed"), ana))).isEmpty();
        mvc.perform(get(foto)).andExpect(status().isNotFound());
    }

    @Test
    void excluirABolhaApagaAsPublicacoesDela() throws Exception {
        entrar(ana, comunidade);
        entrar(bia, comunidade);
        String corpo = comToken(publicacao(comunidade, "Vai sumir", null).file(imagem("f.png")), ana)
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.id");
        String foto = JsonPath.read(corpo, "$.midias[0].url");
        curtir(bia, id);
        comToken(post("/api/publicacoes/" + id + "/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"Que pena\"}"), bia).andExpect(status().isCreated());
        comToken(post("/api/publicacoes/" + id + "/salva"), bia).andExpect(status().isNoContent());
        comToken(post("/api/publicacoes/" + id + "/republicacao"), bia).andExpect(status().isNoContent());
        String doCurso = idDe(publicar(ana, curso, "Fica", null));

        comToken(delete("/api/bolhas/" + comunidade), equipe).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("select id::text from publicacao", String.class)).containsExactly(doCurso);
        assertThat(jdbc.queryForObject("select count(*) from comentario", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reacao", Long.class)).isZero();
        mvc.perform(get(foto)).andExpect(status().isNotFound());
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
        String corpo = comToken(post("/api/bolhas").contentType(MediaType.APPLICATION_JSON).content(json), equipe)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private void entrar(Conta conta, UUID bolha) throws Exception {
        comToken(post("/api/bolhas/" + bolha + "/participacao"), conta).andExpect(status().is2xxSuccessful());
    }

    private void curtir(Conta conta, String publicacao) throws Exception {
        comToken(post("/api/publicacoes/" + publicacao + "/curtida"), conta).andExpect(status().isNoContent());
    }

    private ResultActions comToken(AbstractMockHttpServletRequestBuilder<?> requisicao, Conta conta) throws Exception {
        return mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()));
    }

    private ResultActions publicar(Conta conta, UUID bolha, String texto, String link) throws Exception {
        return comToken(publicacao(bolha, texto, link), conta);
    }

    private ResultActions editar(Conta conta, String id, String texto) throws Exception {
        return comToken(put("/api/publicacoes/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\": \"%s\"}".formatted(texto)), conta);
    }

    /** O multipart de uma publicação nova, só com a parte {@code dados}; os arquivos o teste acrescenta. */
    private static MockMultipartHttpServletRequestBuilder publicacao(UUID bolha, String texto, String link) {
        String json = link == null
                ? "{\"bolhaId\": \"%s\", \"conteudo\": \"%s\"}".formatted(bolha, texto)
                : "{\"bolhaId\": \"%s\", \"conteudo\": \"%s\", \"link\": \"%s\"}".formatted(bolha, texto, link);
        return multipart("/api/publicacoes").file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)));
    }

    private static MockMultipartFile imagem(String nome) {
        return new MockMultipartFile("imagens", nome, "image/png", ServidorPaginas.png(640, 480));
    }

    private static MockMultipartFile video(String nome, byte[] conteudo) {
        return new MockMultipartFile("video", nome, "video/mp4", conteudo);
    }

    private static byte[] mp4() {
        byte[] conteudo = new byte[2048];
        byte[] cabecalho = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(cabecalho, 0, conteudo, 0, cabecalho.length);
        return conteudo;
    }

    private static String idDe(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static List<String> ids(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.itens[*].publicacao.id");
    }

    /** A marca do post mais novo, que vem na primeira página do feed. */
    private static String topo(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.topo");
    }

    private ResultActions novas(MockHttpServletRequestBuilder requisicao, String desde, Conta conta) throws Exception {
        return comToken(requisicao.param("desde", desde), conta).andExpect(status().isOk());
    }

    private static List<String> autores(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.autores[*].username");
    }
}
