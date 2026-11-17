package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.campusconnect.aovivo.service.CanalAoVivo;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AoVivoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CanalAoVivo canal;

    private UUID anaId;
    private String anaToken;

    @BeforeEach
    void criarConta() throws Exception {
        jdbc.execute("truncate usuario, bolha cascade");
        String corpo = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "Ana", "username": "ana", "email": "ana@exemplo.com", "senha": "senha-forte-123"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        anaId = UUID.fromString(JsonPath.read(corpo, "$.usuario.id"));
        anaToken = JsonPath.read(corpo, "$.accessToken");
    }

    @AfterEach
    void fecharConexoes() {
        canal.encerrarTodas();
    }

    @Test
    void semLoginNaoAbre() throws Exception {
        mvc.perform(get("/api/aovivo")).andExpect(status().isUnauthorized());
    }

    @Test
    void aConexaoComecaComProntoERecebeOsEventos() throws Exception {
        MvcResult conexao = abrir();

        assertThat(conexao.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(conexao.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(conexao.getResponse().getContentAsString()).contains("event:pronto");
        assertThat(canal.abertasDe(anaId)).isEqualTo(1);

        canal.enviar(List.of(anaId), "teste", Map.of("texto", "Olá"));

        assertThat(conexao.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("event:teste")
                .contains("\"texto\":\"Olá\"");
    }

    /** No fim do prazo, a conexão termina normalmente (200), e não com o erro de tempo esgotado do Spring. */
    @Test
    void noFimDoPrazoAConexaoFechaSemErro() throws Exception {
        MvcResult conexao = abrir();
        MockAsyncContext contexto = (MockAsyncContext) conexao.getRequest().getAsyncContext();

        for (AsyncListener ouvinte : contexto.getListeners()) {
            ouvinte.onTimeout(new AsyncEvent(contexto));
        }
        mvc.perform(asyncDispatch(conexao)).andExpect(status().isOk());
        for (AsyncListener ouvinte : contexto.getListeners()) {
            ouvinte.onComplete(new AsyncEvent(contexto));
        }

        assertThat(canal.abertasDe(anaId)).isZero();
    }

    private MvcResult abrir() throws Exception {
        return mvc.perform(get("/api/aovivo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anaToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();
    }
}
