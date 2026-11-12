package br.com.campusconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.arquivo.service.VideoInvalidoException;
import br.com.campusconnect.arquivo.service.VideoService;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ArquivoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VideoService videos;

    @Autowired
    private ArquivoService arquivos;

    @Value("${campusconnect.arquivos.diretorio}")
    private Path diretorio;

    @Test
    void videoMp4EhGravadoEEntregueComAvanco() throws Exception {
        byte[] conteudo = mp4(1000);
        String url = videos.salvar(new ByteArrayInputStream(conteudo), conteudo.length);
        assertThat(url).matches("/api/arquivos/[0-9a-f]{32}\\.mp4");

        byte[] inteiro = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(inteiro).isEqualTo(conteudo);

        // O <video> pede pedaços para a pessoa poder avançar sem baixar tudo.
        byte[] pedaco = mvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 100-199/1000"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pedaco).isEqualTo(Arrays.copyOfRange(conteudo, 100, 200));
    }

    @Test
    void videoWebmGanhaAExtensaoEOTipoCertos() throws Exception {
        byte[] conteudo = new byte[300];
        conteudo[0] = 0x1A;
        conteudo[1] = 0x45;
        conteudo[2] = (byte) 0xDF;
        conteudo[3] = (byte) 0xA3;
        String url = videos.salvar(new ByteArrayInputStream(conteudo), conteudo.length);

        assertThat(url).endsWith(".webm");
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/webm"));
    }

    @Test
    void arquivoQueNaoEhVideoEhRecusadoSemSobrarNada() throws Exception {
        List<Path> antes = arquivosNaPasta();
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> videos.salvar(new ByteArrayInputStream(html), html.length))
                .isInstanceOf(VideoInvalidoException.class)
                .hasMessage("Envie um vídeo MP4 ou WebM.");
        assertThat(arquivosNaPasta()).containsExactlyInAnyOrderElementsOf(antes);
    }

    @Test
    void videoAcimaDoLimiteEhRecusadoMesmoMentindoOTamanho() throws Exception {
        List<Path> antes = arquivosNaPasta();

        assertThatThrownBy(() -> videos.salvar(new ByteArrayInputStream(mp4(10)), VideoService.TAMANHO_MAXIMO + 1))
                .isInstanceOf(VideoInvalidoException.class)
                .hasMessage("Envie um vídeo de até 50 MB.");

        // Declara 10 bytes, mas manda 50 MB e mais um: a cópia para no limite e apaga o pedaço gravado.
        assertThatThrownBy(() -> videos.salvar(mp4Infinito(VideoService.TAMANHO_MAXIMO + 1), 10))
                .isInstanceOf(VideoInvalidoException.class)
                .hasMessage("Envie um vídeo de até 50 MB.");
        assertThat(arquivosNaPasta()).containsExactlyInAnyOrderElementsOf(antes);
    }

    @Test
    void nomeForaDoPadraoNaoSaiDaPasta() throws Exception {
        assertThat(arquivos.localizar("../application.yml")).isEmpty();
        assertThat(arquivos.localizar("0123456789abcdef0123456789abcdef.html")).isEmpty();
        mvc.perform(get("/api/arquivos/0123456789abcdef0123456789abcdef.mp4")).andExpect(status().isNotFound());
    }

    @Test
    void imagemAcimaDe5MBContinuaRecusadaNaFotoDePerfil() throws Exception {
        jdbc.execute("truncate usuario, bolha cascade");
        String corpo = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomeCompleto": "Ana Beatriz", "username": "ana.bc", "email": "ana@exemplo.com", "senha": "senha-forte-123"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.accessToken");

        byte[] seisMB = new byte[6 * 1024 * 1024];
        mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "foto.png", "image/png", seisMB))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.arquivo").value("Envie uma imagem de até 5 MB."));
    }

    /** Começo de um MP4 de verdade ({@code ftyp} a partir do quinto byte), completado com bytes que mudam. */
    private static byte[] mp4(int tamanho) {
        byte[] conteudo = new byte[tamanho];
        byte[] cabecalho = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(cabecalho, 0, conteudo, 0, Math.min(cabecalho.length, tamanho));
        for (int i = cabecalho.length; i < tamanho; i++) {
            conteudo[i] = (byte) i;
        }
        return conteudo;
    }

    /** Um MP4 de {@code tamanho} bytes gerado aos poucos, sem ocupar a memória. */
    private static InputStream mp4Infinito(long tamanho) {
        byte[] cabecalho = mp4(12);
        return new InputStream() {
            private long lidos;

            @Override
            public int read() {
                if (lidos >= tamanho) {
                    return -1;
                }
                return lidos < cabecalho.length ? cabecalho[(int) lidos++] : (int) (lidos++ & 0x7F);
            }

            @Override
            public int read(byte[] destino, int inicio, int quantos) {
                if (lidos >= tamanho) {
                    return -1;
                }
                int n = (int) Math.min(quantos, tamanho - lidos);
                for (int i = 0; i < n; i++) {
                    destino[inicio + i] = (byte) read();
                }
                return n;
            }
        };
    }

    private List<Path> arquivosNaPasta() throws Exception {
        if (!Files.isDirectory(diretorio)) {
            return List.of();
        }
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            return arquivos.toList();
        }
    }
}
