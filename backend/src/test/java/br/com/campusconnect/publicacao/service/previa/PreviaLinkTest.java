package br.com.campusconnect.publicacao.service.previa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.campusconnect.ServidorPaginas;
import br.com.campusconnect.arquivo.config.PropriedadesArquivos;
import br.com.campusconnect.arquivo.processamento.ProcessadorImagem;
import br.com.campusconnect.arquivo.service.ArquivoService;
import br.com.campusconnect.arquivo.service.ImagemService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreviaLinkTest {

    @TempDir
    Path pasta;

    private ServidorPaginas servidor;
    private ArquivoService arquivos;
    private PreviaLinkService previas;

    @BeforeEach
    void subir() {
        servidor = new ServidorPaginas();
        arquivos = new ArquivoService(new PropriedadesArquivos(pasta));
        previas = new PreviaLinkService(new ImagemService(new ProcessadorImagem(), arquivos), new PropriedadesPrevia(true));
    }

    @AfterEach
    void descer() throws Exception {
        servidor.close();
        previas.fechar();
    }

    // ------------------------------------------------------------ filtro de endereços (SSRF)

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "10.0.0.5", "172.16.3.4", "192.168.0.10", "169.254.169.254",
            "100.64.0.1", "100.127.255.254", "0.0.0.0", "255.255.255.255", "224.0.0.1", "::1", "::", "fe80::1",
            "fd00::1", "fc12::5", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a9fe:a9fe", "2002:7f00:1::1"})
    void enderecosInternosSaoRecusados(String endereco) throws Exception {
        assertThat(FiltroEnderecos.interno(InetAddress.getByName(endereco))).as(endereco).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "172.32.0.1", "2001:4860:4860::8888",
            "::ffff:8.8.8.8"})
    void enderecosPublicosPassam(String endereco) throws Exception {
        assertThat(FiltroEnderecos.interno(InetAddress.getByName(endereco))).as(endereco).isFalse();
    }

    @Test
    void semPermissaoONomeQueApontaParaDentroEhRecusadoNaResolucao() {
        assertThatThrownBy(() -> new FiltroEnderecos(false).resolve("localhost"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void emProducaoUmLinkParaORedeLocalNaoTemPrevia() throws Exception {
        servidor.html("/pagina", paginaCompleta(servidor.url("/imagem.png")));
        PreviaLinkService producao = new PreviaLinkService(
                new ImagemService(new ProcessadorImagem(), arquivos), new PropriedadesPrevia(false));
        try {
            assertThat(producao.buscar(servidor.url("/pagina"))).isEmpty();
            assertThat(producao.buscar("http://localhost/")).isEmpty();
            assertThat(producao.buscar("http://169.254.169.254/latest/meta-data/")).isEmpty();
        } finally {
            producao.fechar();
        }
    }

    // ------------------------------------------------------------ leitura da página

    @Test
    void leAsTagsOpenGraphEGuardaUmaCopiaDaImagem() throws Exception {
        servidor.html("/pagina", paginaCompleta("/imagem.png"))
                .responder("/imagem.png", 200, "image/png", ServidorPaginas.png(1200, 630));

        PreviaLink previa = previas.buscar(servidor.url("/pagina")).orElseThrow();

        assertThat(previa.titulo()).isEqualTo("Semana Acadêmica 2026");
        assertThat(previa.descricao()).isEqualTo("Palestras, oficinas e muito café.");
        assertThat(previa.site()).isEqualTo("Campus Eventos");
        assertThat(previa.imagemUrl()).matches("/api/arquivos/[0-9a-f]{32}\\.jpg");
        Path copia = arquivos.localizar(previa.imagemUrl().substring("/api/arquivos/".length())).orElseThrow();
        assertThat(Files.size(copia)).isPositive();
    }

    @Test
    void semOpenGraphUsaOTituloEADescricaoDaPagina() {
        servidor.html("/simples", """
                <html><head><title>  Página   simples </title>
                <meta name="description" content="Uma descrição comum."></head><body>oi</body></html>
                """);

        PreviaLink previa = previas.buscar(servidor.url("/simples")).orElseThrow();

        assertThat(previa.titulo()).isEqualTo("Página simples");
        assertThat(previa.descricao()).isEqualTo("Uma descrição comum.");
        assertThat(previa.site()).isEqualTo("127.0.0.1");
        assertThat(previa.imagemUrl()).isNull();
    }

    @Test
    void segueRedirecionamentosAteTres() {
        servidor.html("/destino", "<title>Chegou</title>")
                .redirecionar("/r1", "/r2").redirecionar("/r2", "/r3").redirecionar("/r3", "/destino")
                .redirecionar("/loop", "/loop");

        assertThat(previas.buscar(servidor.url("/r1")).map(PreviaLink::titulo)).contains("Chegou");
        assertThat(previas.buscar(servidor.url("/loop"))).isEmpty();
    }

    @Test
    void imagemQueNaoEhJpgNemPngNaoDerrubaAPrevia() {
        servidor.html("/svg", paginaCompleta("/imagem.svg"))
                .responder("/imagem.svg", 200, "image/svg+xml", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        PreviaLink previa = previas.buscar(servidor.url("/svg")).orElseThrow();

        assertThat(previa.titulo()).isEqualTo("Semana Acadêmica 2026");
        assertThat(previa.imagemUrl()).isNull();
    }

    @Test
    void respostasQueNaoServemFicamSemPrevia() {
        servidor.responder("/json", 200, "application/json", "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8))
                .responder("/nada", 404, "text/html", "<title>Não achei</title>".getBytes(StandardCharsets.UTF_8))
                .responder("/grande", 200, "text/html", new byte[2 * 1024 * 1024])
                .html("/vazia", "<html><body>sem título</body></html>");

        assertThat(previas.buscar(servidor.url("/json"))).isEmpty();
        assertThat(previas.buscar(servidor.url("/nada"))).isEmpty();
        assertThat(previas.buscar(servidor.url("/grande"))).isEmpty();
        assertThat(previas.buscar(servidor.url("/vazia"))).isEmpty();
        assertThat(previas.buscar("ftp://127.0.0.1/arquivo")).isEmpty();
        assertThat(previas.buscar("nao é url")).isEmpty();
    }

    @Test
    void paginaLentaDesisteNoTempoLimite() {
        servidor.lento("/lenta", Duration.ofSeconds(8), "<title>Tarde demais</title>");

        Instant inicio = Instant.now();
        Optional<PreviaLink> previa = previas.buscar(servidor.url("/lenta"));

        assertThat(previa).isEmpty();
        assertThat(Duration.between(inicio, Instant.now())).isLessThan(Duration.ofSeconds(7));
    }

    private static String paginaCompleta(String imagem) {
        return """
                <html><head>
                <meta property="og:title" content="Semana Acadêmica 2026">
                <meta property="og:description" content="Palestras, oficinas e muito café.">
                <meta property="og:site_name" content="Campus Eventos">
                <meta property="og:image" content="%s">
                <title>outro título</title>
                </head><body></body></html>
                """.formatted(imagem);
    }
}
