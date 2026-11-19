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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * As notificações nascem dos eventos dos outros módulos, depois do commit de cada requisição. Como o MockMvc espera
 * a requisição terminar, quando ela volta a notificação já está gravada.
 */
class NotificacoesApiTest extends ApiMarketplaceTeste {

    private static final String TEXTO_DO_POST = "Alguém tem o PDF da aula de Cálculo?";

    private String post;

    @BeforeEach
    void publicarNoCurso() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        entrar(caio, curso);
        post = publicar(ana, curso, TEXTO_DO_POST);
    }

    // ------------------------------------------------------------------ curtidas e republicações

    @Test
    void curtidasDoMesmoPostViramUmaNotificacaoSo() throws Exception {
        curtir(bia).andExpect(status().isNoContent());
        curtir(caio).andExpect(status().isNoContent());
        curtir(bia).andExpect(status().isNoContent()); // de novo: não avisa duas vezes

        assertThat(naoLidas(ana)).isEqualTo(1);
        assertThat(naoLidas(bia)).isZero();
        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("CURTIDA_PUBLICACAO"))
                .andExpect(jsonPath("$.itens[0].total").value(2))
                .andExpect(jsonPath("$.itens[0].pessoas[*].username").value(contains("caio", "bia")))
                .andExpect(jsonPath("$.itens[0].publicacaoId").value(post))
                .andExpect(jsonPath("$.itens[0].trecho").value(TEXTO_DO_POST))
                .andExpect(jsonPath("$.itens[0].nova").value(true))
                .andExpect(jsonPath("$.proximo").value(nullValue()));

        // A republicação é outro grupo.
        comToken(post("/api/publicacoes/" + post + "/republicacao"), bia).andExpect(status().isNoContent());
        notificacoes(ana)
                .andExpect(jsonPath("$.itens[*].tipo").value(contains("REPUBLICACAO", "CURTIDA_PUBLICACAO")));
        comToken(delete("/api/publicacoes/" + post + "/republicacao"), bia).andExpect(status().isNoContent());
        notificacoes(ana).andExpect(jsonPath("$.itens[*].tipo").value(contains("CURTIDA_PUBLICACAO")));
    }

    @Test
    void descurtirAntesDeVerApagaOAvisoMasDepoisDeVerNao() throws Exception {
        curtir(bia);
        descurtir(bia);
        assertThat(naoLidas(ana)).isZero();
        notificacoes(ana).andExpect(jsonPath("$.itens", hasSize(0)));

        curtir(bia);
        lerTudo(ana);
        assertThat(naoLidas(ana)).isZero();

        // Depois de vista, a notificação fica, e curtir de novo não avisa outra vez.
        descurtir(bia);
        curtir(bia);
        assertThat(naoLidas(ana)).isZero();
        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].nova").value(false));
    }

    @Test
    void ninguemEhAvisadoDoQueFezNoProprioPost() throws Exception {
        curtir(ana).andExpect(status().isNoContent());
        comentar(ana, post, "Eu mesma respondo", null);

        assertThat(naoLidas(ana)).isZero();
    }

    // ------------------------------------------------------------------ comentários

    @Test
    void comentarioERespostaAvisamQuemEscreveu() throws Exception {
        String daBia = comentar(bia, post, "Eu tenho,\n te mando", null);
        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("NOVO_COMENTARIO"))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("bia"))
                .andExpect(jsonPath("$.itens[0].trecho").value("Eu tenho, te mando"))
                .andExpect(jsonPath("$.itens[0].publicacaoId").value(post));

        // A Ana responde à Bia: a Bia é avisada, e a Ana, que escreveu o post, não é avisada do que ela fez.
        comentar(ana, post, "Valeu!", daBia);
        notificacoes(bia)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("RESPOSTA_COMENTARIO"))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("ana.bc"));
        assertThat(naoLidas(ana)).isEqualTo(1);

        // O Caio responde à Bia no post da Ana: as duas são avisadas.
        String doCaio = comentar(caio, post, "Me manda também", daBia);
        notificacoes(bia).andExpect(jsonPath("$.itens[*].tipo").value(contains("RESPOSTA_COMENTARIO",
                "RESPOSTA_COMENTARIO")));
        notificacoes(ana)
                .andExpect(jsonPath("$.itens[*].tipo").value(contains("NOVO_COMENTARIO", "NOVO_COMENTARIO")))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("caio"));

        // O comentário excluído leva os avisos dele.
        comToken(delete("/api/publicacoes/comentarios/" + doCaio), caio).andExpect(status().isNoContent());
        assertThat(naoLidas(bia)).isEqualTo(1);
        assertThat(naoLidas(ana)).isEqualTo(1);
    }

    @Test
    void curtidasNoComentarioAvisamQuemComentouJuntas() throws Exception {
        String daBia = comentar(bia, post, "Eu tenho", null);
        comToken(post("/api/publicacoes/comentarios/" + daBia + "/curtida"), ana).andExpect(status().isNoContent());
        comToken(post("/api/publicacoes/comentarios/" + daBia + "/curtida"), caio).andExpect(status().isNoContent());

        notificacoes(bia)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("CURTIDA_COMENTARIO"))
                .andExpect(jsonPath("$.itens[0].total").value(2))
                .andExpect(jsonPath("$.itens[0].trecho").value("Eu tenho"))
                .andExpect(jsonPath("$.itens[0].publicacaoId").value(post));

        comToken(delete("/api/publicacoes/comentarios/" + daBia + "/curtida"), caio)
                .andExpect(status().isNoContent());
        notificacoes(bia).andExpect(jsonPath("$.itens[0].total").value(1));
    }

    @Test
    void excluirOPostApagaAsNotificacoesDele() throws Exception {
        curtir(bia);
        comentar(caio, post, "Também quero", null);
        assertThat(naoLidas(ana)).isEqualTo(2);

        comToken(delete("/api/publicacoes/" + post), ana).andExpect(status().isNoContent());

        assertThat(naoLidas(ana)).isZero();
        notificacoes(ana).andExpect(jsonPath("$.itens", hasSize(0)));
    }

    @Test
    void excluirABolhaApagaAsNotificacoesDosPostsDela() throws Exception {
        entrar(ana, comunidade);
        entrar(bia, comunidade);
        String doClube = publicar(ana, comunidade, "Torneio na sexta");
        comToken(post("/api/publicacoes/" + doClube + "/curtida"), bia).andExpect(status().isNoContent());
        curtir(bia);
        assertThat(naoLidas(ana)).isEqualTo(2);

        comToken(delete("/api/bolhas/" + comunidade), equipe).andExpect(status().isNoContent());

        notificacoes(ana).andExpect(jsonPath("$.itens[*].publicacaoId").value(contains(post)));
        assertThat(naoLidas(ana)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ conexões e bloqueio

    @Test
    void pedidoDeConexaoAvisaERetirarApaga() throws Exception {
        conectar(bia, ana);
        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("SOLICITACAO_CONEXAO"))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("bia"))
                .andExpect(jsonPath("$.itens[0].trecho").value(nullValue()));

        comToken(delete("/api/conexoes/com/" + ana.id() + "/pedido"), bia).andExpect(status().is2xxSuccessful());
        assertThat(naoLidas(ana)).isZero();
        notificacoes(ana).andExpect(jsonPath("$.itens", hasSize(0)));

        // Pedir de novo avisa de novo; aceitar avisa quem pediu.
        conectar(bia, ana);
        assertThat(naoLidas(ana)).isEqualTo(1);
        conectar(ana, bia);
        notificacoes(bia)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("CONEXAO_ACEITA"))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("ana.bc"));
    }

    @Test
    void pedirDeNovoDepoisDeDesfazerTrazOAvisoDeVoltaComoNovo() throws Exception {
        conectar(bia, ana);
        conectar(ana, bia);
        lerTudo(ana);
        lerTudo(bia);
        comToken(delete("/api/conexoes/com/" + bia.id()), ana).andExpect(status().is2xxSuccessful());

        conectar(bia, ana);

        assertThat(naoLidas(ana)).isEqualTo(1);
        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].nova").value(true));
    }

    @Test
    void bloqueioApagaOQueUmAvisouAoOutroENadaMaisPassa() throws Exception {
        curtir(bia);
        comentar(bia, post, "Oi", null);
        curtir(caio);
        assertThat(naoLidas(ana)).isEqualTo(2);

        comToken(post("/api/conexoes/com/" + bia.id() + "/bloqueio"), ana).andExpect(status().is2xxSuccessful());

        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].pessoas[*].username").value(contains("caio")));
        comentar(bia, post, "Oi de novo", null);
        comToken(post("/api/publicacoes/" + post + "/republicacao"), bia).andExpect(status().isNoContent());
        assertThat(naoLidas(ana)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ marketplace

    @Test
    void avaliacaoDoAnuncioAvisaQuemAnunciouUmaVezSo() throws Exception {
        String anuncio = anunciado(ana, servico(geral));
        String avaliacao = JsonPath.read(corpo(avaliar(bia, anuncio, 5).andExpect(status().isOk())), "$.id");
        avaliar(bia, anuncio, 3).andExpect(status().isOk()); // trocar a nota não avisa de novo

        notificacoes(ana)
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tipo").value("AVALIACAO_ANUNCIO"))
                .andExpect(jsonPath("$.itens[0].anuncioId").value(anuncio))
                .andExpect(jsonPath("$.itens[0].trecho").value("Site em React"))
                .andExpect(jsonPath("$.itens[0].pessoas[0].username").value("bia"));

        comToken(delete("/api/anuncios/" + anuncio + "/avaliacoes/" + avaliacao), bia)
                .andExpect(status().isNoContent());
        assertThat(naoLidas(ana)).isZero();

        // O anúncio excluído leva as avaliações que ainda avisavam.
        avaliar(caio, anuncio, 4).andExpect(status().isOk());
        assertThat(naoLidas(ana)).isEqualTo(1);
        comToken(delete("/api/anuncios/" + anuncio), ana).andExpect(status().isNoContent());
        assertThat(naoLidas(ana)).isZero();
    }

    // ------------------------------------------------------------------ leitura e páginas

    @Test
    void lerZeraOContadorMasNaoAQueChegaDepoisDeATelaAbrir() throws Exception {
        curtir(bia);
        String primeira = corpo(notificacoes(ana));
        String ate = JsonPath.read(primeira, "$.maisNovaEm");
        curtir(caio); // chega enquanto a tela está aberta

        ler(ana, ate).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));

        assertThat(naoLidas(ana)).isEqualTo(1);
        notificacoes(ana)
                .andExpect(jsonPath("$.itens[0].nova").value(true))
                .andExpect(jsonPath("$.itens[0].total").value(2));
        ler(ana, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.ate").exists());
    }

    @Test
    void paginaTemVinteEContinuaPeloCursor() throws Exception {
        for (int i = 0; i < 25; i++) {
            comentar(bia, post, "Comentário " + i, null);
        }

        String primeira = corpo(notificacoes(ana).andExpect(jsonPath("$.itens", hasSize(20))));
        assertThat(JsonPath.<String>read(primeira, "$.itens[0].trecho")).isEqualTo("Comentário 24");
        assertThat(JsonPath.<String>read(primeira, "$.maisNovaEm")).isNotNull();
        String proximo = JsonPath.read(primeira, "$.proximo");

        String segunda = corpo(comToken(get("/api/notificacoes").param("cursor", proximo), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(5)))
                .andExpect(jsonPath("$.proximo").value(nullValue()))
                .andExpect(jsonPath("$.maisNovaEm").value(nullValue())));
        assertThat(JsonPath.<String>read(segunda, "$.itens[4].trecho")).isEqualTo("Comentário 0");

        List<String> todos = new ArrayList<>(JsonPath.read(primeira, "$.itens[*].id"));
        todos.addAll(JsonPath.read(segunda, "$.itens[*].id"));
        assertThat(new HashSet<>(todos)).hasSize(25);

        comToken(get("/api/notificacoes").param("cursor", "nao-e-cursor"), ana)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cursor").exists());
    }

    @Test
    void precisaEstarLogado() throws Exception {
        mvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notificacoes/nao-lidas")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions notificacoes(Conta conta) throws Exception {
        return comToken(get("/api/notificacoes"), conta).andExpect(status().isOk());
    }

    private long naoLidas(Conta conta) throws Exception {
        return JsonPath.<Number>read(corpo(comToken(get("/api/notificacoes/nao-lidas"), conta)
                .andExpect(status().isOk())), "$.total").longValue();
    }

    private ResultActions ler(Conta conta, String ate) throws Exception {
        String json = ate == null ? "{}" : "{\"ate\": \"%s\"}".formatted(ate);
        return comToken(post("/api/notificacoes/leitura").contentType(MediaType.APPLICATION_JSON).content(json),
                conta);
    }

    private void lerTudo(Conta conta) throws Exception {
        String ate = JsonPath.read(corpo(notificacoes(conta)), "$.maisNovaEm");
        ler(conta, ate).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    private ResultActions curtir(Conta conta) throws Exception {
        return comToken(post("/api/publicacoes/" + post + "/curtida"), conta);
    }

    private void descurtir(Conta conta) throws Exception {
        comToken(delete("/api/publicacoes/" + post + "/curtida"), conta).andExpect(status().isNoContent());
    }

    private void conectar(Conta quem, Conta outro) throws Exception {
        comToken(post("/api/conexoes/com/" + outro.id()), quem).andExpect(status().is2xxSuccessful());
    }

    private ResultActions avaliar(Conta conta, String anuncio, int nota) throws Exception {
        return comToken(put("/api/anuncios/" + anuncio + "/avaliacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\": %d}".formatted(nota)), conta);
    }

    private String publicar(Conta conta, UUID bolha, String texto) throws Exception {
        String json = "{\"bolhaId\": \"%s\", \"conteudo\": \"%s\"}".formatted(bolha, texto);
        return JsonPath.read(corpo(comToken(multipart("/api/publicacoes").file(new MockMultipartFile("dados", "",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8))), conta)
                .andExpect(status().isCreated())), "$.id");
    }

    private String comentar(Conta conta, String publicacao, String texto, String respondendoA) throws Exception {
        String conteudo = texto.replace("\n", "\\n");
        String json = respondendoA == null
                ? "{\"conteudo\": \"%s\"}".formatted(conteudo)
                : "{\"conteudo\": \"%s\", \"respondendoA\": \"%s\"}".formatted(conteudo, respondendoA);
        return JsonPath.read(corpo(comToken(post("/api/publicacoes/" + publicacao + "/comentarios")
                .contentType(MediaType.APPLICATION_JSON).content(json), conta)
                .andExpect(status().isCreated())), "$.id");
    }
}
