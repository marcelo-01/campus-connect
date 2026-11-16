package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AnuncioApiTest extends ApiMarketplaceTeste {

    @Value("${campusconnect.arquivos.diretorio}")
    private Path uploads;

    // ------------------------------------------------------------------ anunciar

    @Test
    void anunciaUmServicoComFotosEUmProdutoGratisSemFoto() throws Exception {
        entrar(ana, curso);

        String corpo = corpo(anunciar(ana, servico(curso).com("preco", 120.5), fotos(2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/anuncios/")))
                .andExpect(jsonPath("$.tipo").value("SERVICO"))
                .andExpect(jsonPath("$.titulo").value("Site em React"))
                .andExpect(jsonPath("$.categoria.slug").value("tecnologia"))
                .andExpect(jsonPath("$.modalidadePreco").value("FIXO"))
                .andExpect(jsonPath("$.preco").value(120.5))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.anunciante.username").value("ana.bc"))
                .andExpect(jsonPath("$.bolhas", hasSize(1)))
                .andExpect(jsonPath("$.bolhas[0].id").value(curso.toString()))
                .andExpect(jsonPath("$.imagens", hasSize(2)))
                .andExpect(jsonPath("$.totalAvaliacoes").value(0))
                .andExpect(jsonPath("$.media").value(nullValue()))
                .andExpect(jsonPath("$.podeEditar").value(true))
                .andExpect(jsonPath("$.podeAvaliar").value(false))
                .andExpect(jsonPath("$.publicadoEm").isNotEmpty()));
        String capa = JsonPath.read(corpo, "$.capaUrl");
        assertThat(capa).isEqualTo(JsonPath.read(corpo, "$.imagens[0]"));
        mvc.perform(get(capa)).andExpect(status().isOk());

        anunciar(ana, produto(geral))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("PRODUTO"))
                .andExpect(jsonPath("$.modalidadePreco").value("GRATUITO"))
                .andExpect(jsonPath("$.preco").value(nullValue()))
                .andExpect(jsonPath("$.capaUrl").value(nullValue()))
                .andExpect(jsonPath("$.imagens", hasSize(0)));
    }

    @Test
    void recusaAnunciosInvalidos() throws Exception {
        entrar(ana, curso);

        anunciar(ana, servico(curso).com("modalidadePreco", "A_COMBINAR"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.preco").isNotEmpty());
        anunciar(ana, servico(curso).com("preco", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.preco").isNotEmpty());
        anunciar(ana, servico(curso).com("preco", 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.preco").isNotEmpty());
        anunciar(ana, servico(curso).categoria("livros-apostilas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.categoriaId").value("Escolha uma categoria de serviço."));
        anunciar(ana, servico(curso).com("titulo", "ab"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.titulo").isNotEmpty());
        anunciar(ana, servico(curso).com("descricao", "  "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.descricao").isNotEmpty());
        anunciar(ana, servico())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.bolhaIds").isNotEmpty());
        anunciar(ana, servico(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaIds").value("Escolha até 5 bolhas."));
        anunciar(ana, servico(curso, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaIds").value("Escolha bolhas que existam."));
        anunciar(ana, servico(curso), fotos(7))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.imagens").isNotEmpty());
        comToken(get("/api/anuncios").param("cursor", "nao-e-um-cursor"), ana)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.cursor").isNotEmpty());
        comToken(get("/api/anuncios").param("ordem", "barato"), ana)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.ordem").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from anuncio", Long.class)).isZero();
    }

    @Test
    void soAnunciaEmBolhasDeQueEMembroERepetirABolhaContaUmaVez() throws Exception {
        entrar(ana, curso);

        anunciar(bia, servico(curso)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Entre na bolha para anunciar nela."));
        anunciar(ana, servico(curso, comunidade)).andExpect(status().isForbidden());

        anunciar(ana, servico(curso, curso))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bolhas", hasSize(1)));
    }

    // ------------------------------------------------------------------ vitrine

    @Test
    void oAnuncioDesceDaBolhaParaAsDeDentroMasNaoSobe() throws Exception {
        entrar(ana, curso);
        entrar(bia, geral);
        String naGeral = anunciado(ana, produto(geral));
        String noCurso = anunciado(ana, servico(curso));

        // Bia está só na Geral: vê o que foi para a Geral, mas não o que foi para o Curso.
        assertThat(ids(comToken(get("/api/anuncios"), bia))).containsExactly(naGeral);
        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "rede"), bia)))
                .containsExactlyInAnyOrder(naGeral, noCurso);

        // Ana está no Curso, e por isso também na Geral.
        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "bolhas"), ana)))
                .containsExactlyInAnyOrder(naGeral, noCurso);
        assertThat(ids(comToken(get("/api/anuncios").param("bolhaId", geral.toString()), ana)))
                .containsExactly(naGeral);
        assertThat(ids(comToken(get("/api/anuncios").param("bolhaId", curso.toString()), ana)))
                .containsExactly(noCurso);

        // Caio não está em bolha nenhuma.
        comToken(get("/api/anuncios"), caio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void filtraPorTipoCategoriaEBuscaEOrdenaPorPrecoEAvaliacao() throws Exception {
        entrar(ana, curso);
        String site = anunciado(ana, servico(curso).com("preco", 50));
        String aulas = anunciado(ana, servico(curso).categoria("aulas-particulares").com("titulo", "Aulas 100% online")
                .com("modalidadePreco", "POR_HORA").com("preco", 30));
        String apostila = anunciado(ana, produto(curso));
        String fone = anunciado(ana, produto(curso).categoria("eletronicos").com("titulo", "Fone de ouvido")
                .com("modalidadePreco", "A_COMBINAR"));
        String notebook = anunciado(ana, produto(curso).categoria("eletronicos").com("titulo", "Notebook_usado")
                .com("modalidadePreco", "FIXO").com("preco", 2000));

        assertThat(ids(vitrine("tipo", "PRODUTO"))).containsExactlyInAnyOrder(apostila, fone, notebook);
        assertThat(ids(vitrine("tipo", "servico"))).containsExactlyInAnyOrder(site, aulas);
        assertThat(ids(vitrine("categoria", "eletronicos"))).containsExactlyInAnyOrder(fone, notebook);
        // % e _ valem como texto, e não como curingas.
        assertThat(ids(vitrine("q", "100%"))).containsExactly(aulas);
        assertThat(ids(vitrine("q", "_"))).containsExactly(notebook);
        assertThat(ids(vitrine("q", "landing"))).containsExactlyInAnyOrder(site, aulas, apostila, fone, notebook);

        // Grátis conta como zero; "A combinar" fica no fim nos dois sentidos.
        assertThat(ids(vitrine("ordem", "preco-asc"))).containsExactly(apostila, aulas, site, notebook, fone);
        assertThat(ids(vitrine("ordem", "preco-desc"))).containsExactly(notebook, site, aulas, apostila, fone);

        avaliar(bia, site, 3);
        avaliar(caio, site, 5);
        avaliar(bia, notebook, 5);
        List<String> porNota = ids(vitrine("ordem", "avaliacao"));
        assertThat(porNota.subList(0, 2)).containsExactly(notebook, site);
        assertThat(porNota.subList(2, 5)).containsExactlyInAnyOrder(aulas, apostila, fone);

        vitrine("tipo", "PRODUTO").andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void paginaAVitrineEContaOTotalNaPrimeiraPagina() throws Exception {
        entrar(ana, curso);
        for (int i = 0; i < 21; i++) {
            anunciado(ana, servico(curso).com("titulo", "Anúncio " + i));
        }

        String primeira = corpo(comToken(get("/api/anuncios"), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(20)))
                .andExpect(jsonPath("$.total").value(21)));
        String proximo = JsonPath.read(primeira, "$.proximo");

        comToken(get("/api/anuncios").param("cursor", proximo), ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.proximo").value(nullValue()))
                .andExpect(jsonPath("$.total").value(nullValue()));
    }

    // ------------------------------------------------------------------ gerenciar

    @Test
    void soQuemAnunciouEditaETrocaAsFotos() throws Exception {
        entrar(ana, curso);
        String corpo = corpo(anunciar(ana, servico(curso), fotos(2)).andExpect(status().isCreated()));
        String id = JsonPath.read(corpo, "$.id");
        String primeira = JsonPath.read(corpo, "$.imagens[0]");
        String segunda = JsonPath.read(corpo, "$.imagens[1]");

        long arquivosAntes = arquivosGravados();
        editar(bia, id, servico(curso).com("imagensMantidas", List.of()), foto("intrusa.png"))
                .andExpect(status().isForbidden());
        assertThat(arquivosGravados()).isEqualTo(arquivosAntes);

        String editado = corpo(editar(ana, id, servico(geral).com("titulo", "Site em Angular").com("preco", 300)
                        .com("imagensMantidas", List.of(segunda)), foto("nova.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Site em Angular"))
                .andExpect(jsonPath("$.preco").value(300.0))
                .andExpect(jsonPath("$.bolhas", hasSize(1)))
                .andExpect(jsonPath("$.bolhas[0].id").value(geral.toString()))
                .andExpect(jsonPath("$.imagens", hasSize(2)))
                .andExpect(jsonPath("$.imagens[0]").value(segunda))
                .andExpect(jsonPath("$.capaUrl").value(segunda)));
        String nova = JsonPath.read(editado, "$.imagens[1]");
        mvc.perform(get(nova)).andExpect(status().isOk());
        mvc.perform(get(primeira)).andExpect(status().isNotFound());

        // Recarregar mostra a mesma ordem.
        comToken(get("/api/anuncios/" + id), ana)
                .andExpect(jsonPath("$.imagens[0]").value(segunda))
                .andExpect(jsonPath("$.imagens[1]").value(nova));
    }

    @Test
    void naoDeixaManterAFotoDeOutroAnuncio() throws Exception {
        entrar(ana, curso);
        entrar(bia, curso);
        String daBia = JsonPath.read(corpo(anunciar(bia, servico(curso), fotos(1)).andExpect(status().isCreated())),
                "$.imagens[0]");
        String daAna = anunciado(ana, servico(curso));

        editar(ana, daAna, servico(curso).com("imagensMantidas", List.of(daBia)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.imagensMantidas").isNotEmpty());
        comToken(delete("/api/anuncios/" + daAna), ana).andExpect(status().isNoContent());
        mvc.perform(get(daBia)).andExpect(status().isOk());
    }

    @Test
    void pausadoSomeDaVitrineMasOAnuncianteContinuaVendo() throws Exception {
        entrar(ana, curso);
        String id = anunciado(ana, servico(curso));
        String publicadoEm = JsonPath.read(corpo(comToken(get("/api/anuncios/" + id), ana)), "$.publicadoEm");

        comToken(post("/api/anuncios/" + id + "/pausa"), bia).andExpect(status().isForbidden());
        comToken(post("/api/anuncios/" + id + "/pausa"), ana).andExpect(status().isNoContent());

        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "rede"), bia))).isEmpty();
        comToken(get("/api/anuncios/" + id), bia).andExpect(status().isNotFound());
        comToken(get("/api/anuncios/" + id), ana)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSADO"));
        assertThat(ids(comToken(get("/api/anuncios/usuario/" + ana.id()), ana))).containsExactly(id);
        assertThat(ids(comToken(get("/api/anuncios/usuario/" + ana.id()), bia))).isEmpty();

        comToken(delete("/api/anuncios/" + id + "/pausa"), ana).andExpect(status().isNoContent());
        comToken(get("/api/anuncios/" + id), bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.publicadoEm").value(publicadoEm));
    }

    @Test
    void quemAnunciouOuUmAdministradorExclui() throws Exception {
        entrar(ana, curso);
        String corpo = corpo(anunciar(ana, servico(curso), fotos(1)).andExpect(status().isCreated()));
        String id = JsonPath.read(corpo, "$.id");
        String foto = JsonPath.read(corpo, "$.capaUrl");
        String outro = anunciado(ana, produto(curso));

        comToken(delete("/api/anuncios/" + id), bia).andExpect(status().isForbidden());
        comToken(delete("/api/anuncios/" + id), ana).andExpect(status().isNoContent());
        comToken(delete("/api/anuncios/" + outro), equipe).andExpect(status().isNoContent());

        comToken(get("/api/anuncios/" + id), ana).andExpect(status().isNotFound());
        comToken(delete("/api/anuncios/" + id), ana).andExpect(status().isNotFound());
        assertThat(ids(comToken(get("/api/anuncios/usuario/" + ana.id()), ana))).isEmpty();
        mvc.perform(get(foto)).andExpect(status().isNotFound());
    }

    @Test
    void excluirABolhaTiraOAnuncioDelaSemApagarOAnuncio() throws Exception {
        entrar(ana, comunidade);
        String soNaComunidade = anunciado(ana, servico(comunidade));
        String tambemNoCurso = anunciado(ana, produto(comunidade, curso));

        comToken(delete("/api/bolhas/" + comunidade), equipe).andExpect(status().isNoContent());

        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "rede")
                .param("bolhaId", comunidade.toString()), ana))).isEmpty();
        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "rede"), ana)))
                .containsExactlyInAnyOrder(soNaComunidade, tambemNoCurso);
        assertThat(ids(comToken(get("/api/anuncios/usuario/" + ana.id()), ana)))
                .containsExactlyInAnyOrder(soNaComunidade, tambemNoCurso);
        comToken(get("/api/anuncios/" + soNaComunidade), ana)
                .andExpect(status().isOk()).andExpect(jsonPath("$.bolhas", hasSize(0)));
        comToken(get("/api/anuncios/" + tambemNoCurso), ana)
                .andExpect(jsonPath("$.bolhas", hasSize(1)))
                .andExpect(jsonPath("$.bolhas[0].id").value(curso.toString()));

        editar(ana, soNaComunidade, servico()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bolhaIds").isNotEmpty());
    }

    @Test
    void listaAsCategoriasDeServicoAntesDasDeProduto() throws Exception {
        String corpo = corpo(comToken(get("/api/anuncios/categorias"), ana).andExpect(status().isOk()));
        List<String> tipos = JsonPath.read(corpo, "$[*].tipo");
        List<String> slugs = JsonPath.read(corpo, "$[*].slug");

        assertThat(tipos.indexOf("PRODUTO")).isEqualTo(tipos.lastIndexOf("SERVICO") + 1);
        assertThat(slugs).contains("tecnologia", "aulas-particulares", "livros-apostilas", "eletronicos");
        assertThat(slugs.get(tipos.lastIndexOf("SERVICO"))).isEqualTo("outros-servicos");
        assertThat(slugs.getLast()).isEqualTo("outros-produtos");
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions vitrine(String parametro, String valor) throws Exception {
        return comToken(get("/api/anuncios").param("escopo", "rede").param(parametro, valor), ana);
    }

    private void avaliar(Conta conta, String anuncio, int nota) throws Exception {
        comToken(put("/api/anuncios/" + anuncio + "/avaliacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\": " + nota + "}"), conta).andExpect(status().isOk());
    }

    private long arquivosGravados() throws IOException {
        if (!Files.isDirectory(uploads)) {
            return 0;
        }
        try (Stream<Path> arquivos = Files.list(uploads)) {
            return arquivos.count();
        }
    }
}
