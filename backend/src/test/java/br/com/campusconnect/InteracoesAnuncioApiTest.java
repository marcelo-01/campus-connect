package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class InteracoesAnuncioApiTest extends ApiMarketplaceTeste {

    private String anuncio;

    @BeforeEach
    void anunciarNaGeral() throws Exception {
        entrar(ana, curso);
        anuncio = anunciado(ana, servico(geral));
    }

    // ------------------------------------------------------------------ favoritos

    @Test
    void favoritarEIdempotenteEApareceEmFavoritos() throws Exception {
        favoritar(bia).andExpect(status().isNoContent());
        favoritar(bia).andExpect(status().isNoContent());

        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "favoritos"), bia))).containsExactly(anuncio);
        comToken(get("/api/anuncios").param("escopo", "rede"), bia).andExpect(jsonPath("$.itens[0].favorito").value(true));
        comToken(get("/api/anuncios").param("escopo", "rede"), caio).andExpect(jsonPath("$.itens[0].favorito").value(false));
        comToken(get("/api/anuncios/" + anuncio), bia).andExpect(jsonPath("$.favorito").value(true));
        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "favoritos"), caio))).isEmpty();

        comToken(delete("/api/anuncios/" + anuncio + "/favorito"), bia).andExpect(status().isNoContent());
        comToken(delete("/api/anuncios/" + anuncio + "/favorito"), bia).andExpect(status().isNoContent());
        assertThat(ids(comToken(get("/api/anuncios").param("escopo", "favoritos"), bia))).isEmpty();
    }

    @Test
    void naoFavoritaNemAvaliaAnuncioPausado() throws Exception {
        comToken(post("/api/anuncios/" + anuncio + "/pausa"), ana).andExpect(status().isNoContent());

        favoritar(bia).andExpect(status().isNotFound());
        avaliar(bia, 5, null).andExpect(status().isNotFound());
        comToken(get("/api/anuncios/" + anuncio + "/avaliacoes"), bia).andExpect(status().isNotFound());
        favoritar(bia, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ avaliações

    @Test
    void quemNaoAnunciouAvaliaUmaVezEPodeTrocarANota() throws Exception {
        avaliar(ana, 5, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Você não pode avaliar o próprio anúncio."));
        avaliar(bia, 0, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.nota").isNotEmpty());
        avaliar(bia, 6, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.nota").isNotEmpty());

        avaliar(bia, 4, "Entregou rápido")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(4))
                .andExpect(jsonPath("$.comentario").value("Entregou rápido"))
                .andExpect(jsonPath("$.autor.username").value("bia"))
                .andExpect(jsonPath("$.podeExcluir").value(true));
        avaliar(bia, 2, "  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(2))
                .andExpect(jsonPath("$.comentario").value(nullValue()));
        avaliar(caio, 5, "Recomendo").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from avaliacao_anuncio", Long.class)).isEqualTo(2);
        comToken(get("/api/anuncios/" + anuncio), bia)
                .andExpect(jsonPath("$.media").value(3.5))
                .andExpect(jsonPath("$.totalAvaliacoes").value(2))
                .andExpect(jsonPath("$.minhaAvaliacao.nota").value(2))
                .andExpect(jsonPath("$.podeAvaliar").value(true));
        comToken(get("/api/anuncios/" + anuncio), ana)
                .andExpect(jsonPath("$.minhaAvaliacao").value(nullValue()))
                .andExpect(jsonPath("$.podeAvaliar").value(false));
        comToken(get("/api/anuncios").param("escopo", "rede"), ana)
                .andExpect(jsonPath("$.itens[0].media").value(3.5))
                .andExpect(jsonPath("$.itens[0].totalAvaliacoes").value(2));

        comToken(get("/api/anuncios/" + anuncio + "/avaliacoes"), bia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].autor.username").value("caio"))
                .andExpect(jsonPath("$[0].podeExcluir").value(false))
                .andExpect(jsonPath("$[1].autor.username").value("bia"))
                .andExpect(jsonPath("$[1].podeExcluir").value(true));
    }

    @Test
    void quemAvaliouOuUmAdministradorApagaAAvaliacao() throws Exception {
        String daBia = JsonPath.read(corpo(avaliar(bia, 4, null).andExpect(status().isOk())), "$.id");
        String doCaio = JsonPath.read(corpo(avaliar(caio, 5, null).andExpect(status().isOk())), "$.id");

        comToken(delete("/api/anuncios/" + anuncio + "/avaliacoes/" + daBia), caio).andExpect(status().isForbidden());
        comToken(delete("/api/anuncios/" + anuncio + "/avaliacoes/" + daBia), bia).andExpect(status().isNoContent());
        comToken(delete("/api/anuncios/" + UUID.randomUUID() + "/avaliacoes/" + doCaio), equipe)
                .andExpect(status().isNotFound());
        comToken(get("/api/anuncios/" + anuncio + "/avaliacoes"), equipe)
                .andExpect(jsonPath("$[0].podeExcluir").value(true));
        comToken(delete("/api/anuncios/" + anuncio + "/avaliacoes/" + doCaio), equipe)
                .andExpect(status().isNoContent());

        comToken(get("/api/anuncios/" + anuncio + "/avaliacoes"), bia).andExpect(jsonPath("$", hasSize(0)));
        comToken(get("/api/anuncios/" + anuncio), bia)
                .andExpect(jsonPath("$.media").value(nullValue()))
                .andExpect(jsonPath("$.totalAvaliacoes").value(0));
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions favoritar(Conta conta) throws Exception {
        return favoritar(conta, anuncio);
    }

    private ResultActions favoritar(Conta conta, String id) throws Exception {
        return comToken(post("/api/anuncios/" + id + "/favorito"), conta);
    }

    private ResultActions avaliar(Conta conta, int nota, String comentario) throws Exception {
        String json = comentario == null ? "{\"nota\": %d}".formatted(nota)
                : "{\"nota\": %d, \"comentario\": \"%s\"}".formatted(nota, comentario);
        return comToken(put("/api/anuncios/" + anuncio + "/avaliacao").contentType(MediaType.APPLICATION_JSON)
                .content(json), conta);
    }
}
