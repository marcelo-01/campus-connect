package br.com.campusconnect.publicacao.service.previa;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemService;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * Lê a página de um link para montar a prévia: título, descrição, nome do site e imagem de destaque, das
 * tags Open Graph ({@code og:*}) ou, sem elas, do {@code <title>} e da {@code description}.
 *
 * <p>Buscar uma URL que veio de fora é a porta de entrada de um ataque de SSRF, então a busca é cercada:
 * <ul>
 *   <li>só {@code http} e {@code https}, nas portas 80 e 443, sem proxy do sistema;</li>
 *   <li>endereços internos são recusados na resolução de DNS ({@link FiltroEnderecos}), em cada conexão;</li>
 *   <li>até 3 redirecionamentos, cada um conferido de novo;</li>
 *   <li>tempo curto, só {@code text/html} e no máximo 1 MB lido da página;</li>
 *   <li>a imagem é baixada pelo mesmo caminho, com até 5 MB, e regravada pelo {@link ImagemService}: o
 *       navegador de quem lê o post nunca acessa o site do link.</li>
 * </ul>
 *
 * <p>Nunca lança: sem prévia, o post sai com o cartão simples do link.
 */
@Slf4j
@Service
public class PreviaLinkService {

    static final int LIMITE_PAGINA = 1024 * 1024;
    static final int MAXIMO_REDIRECIONAMENTOS = 3;
    static final Duration PRAZO = Duration.ofSeconds(5);

    private static final Set<Integer> REDIRECIONAMENTOS = Set.of(301, 302, 303, 307, 308);
    private static final Set<String> IMAGENS_ACEITAS = Set.of("image/jpeg", "image/png");

    private final ImagemService imagens;
    private final CloseableHttpClient cliente;
    private final boolean permitirRedeLocal;

    public PreviaLinkService(ImagemService imagens, PropriedadesPrevia propriedades) {
        this.imagens = imagens;
        this.permitirRedeLocal = propriedades.permitirRedeLocal();
        this.cliente = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new FiltroEnderecos(propriedades.permitirRedeLocal()))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(3))
                                .setSocketTimeout(Timeout.ofSeconds(3))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(4))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                        .setRedirectsEnabled(false) // seguidos à mão, um a um, em baixar()
                        .build())
                // Cada busca usa uma conexão nova e fecha no fim: nada sobra de uma busca para outra.
                .setConnectionReuseStrategy((pedido, resposta, contexto) -> false)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .setUserAgent("CampusConnect/1.0 (previa de links)")
                .build();
    }

    @PreDestroy
    void fechar() throws IOException {
        cliente.close();
    }

    public Optional<PreviaLink> buscar(String url) {
        Instant prazo = Instant.now().plus(PRAZO);
        try {
            Optional<Baixado> pagina = baixar(URI.create(url), LIMITE_PAGINA, prazo, true);
            if (pagina.isEmpty()) {
                return Optional.empty();
            }
            Baixado baixado = pagina.get();
            Document documento = Jsoup.parse(new ByteArrayInputStream(baixado.corpo()),
                    baixado.charset() == null ? null : baixado.charset().name(), baixado.endereco().toString());

            String titulo = limitar(meta(documento, "og:title", "twitter:title"), 300);
            if (titulo == null) {
                titulo = limitar(documento.title(), 300);
            }
            String descricao = limitar(meta(documento, "og:description", "twitter:description", "description"), 500);
            String site = limitar(meta(documento, "og:site_name"), 100);
            if (site == null) {
                site = limitar(semWww(baixado.endereco().getHost()), 100);
            }
            String imagemUrl = imagem(documento, prazo);
            if (titulo == null && descricao == null && imagemUrl == null) {
                return Optional.empty();
            }
            return Optional.of(new PreviaLink(titulo, descricao, site, imagemUrl));
        } catch (Exception falha) {
            log.debug("Sem prévia para {}: {}", url, falha.toString());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ leitura da página

    private @Nullable String imagem(Document documento, Instant prazo) {
        Element meta = metaElemento(documento, "og:image", "og:image:url", "twitter:image");
        if (meta == null) {
            return null;
        }
        String endereco = meta.absUrl("content");
        if (endereco.isBlank()) {
            return null;
        }
        try {
            Optional<Baixado> imagem = baixar(URI.create(endereco), (int) ImagemService.TAMANHO_MAXIMO, prazo, false);
            return imagem.map(baixada -> imagens.salvar(baixada.corpo(), FormatoImagem.PREVIA_LINK)).orElse(null);
        } catch (RuntimeException | IOException naoServe) {
            // Imagem que não é JPG ou PNG, endereço estranho, site fora do ar: a prévia sai sem imagem.
            return null;
        }
    }

    private static @Nullable String meta(Document documento, String... chaves) {
        Element elemento = metaElemento(documento, chaves);
        return elemento == null ? null : elemento.attr("content");
    }

    /** A primeira tag {@code <meta property|name="chave" content="...">} com conteúdo, na ordem das chaves. */
    private static @Nullable Element metaElemento(Document documento, String... chaves) {
        for (String chave : chaves) {
            for (Element meta : documento.getElementsByTag("meta")) {
                boolean bate = chave.equalsIgnoreCase(meta.attr("property")) || chave.equalsIgnoreCase(meta.attr("name"));
                if (bate && !meta.attr("content").isBlank()) {
                    return meta;
                }
            }
        }
        return null;
    }

    private static @Nullable String limitar(@Nullable String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replaceAll("\\s+", " ").strip();
        if (limpo.isEmpty()) {
            return null;
        }
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo - 1).strip() + "…";
    }

    private static @Nullable String semWww(@Nullable String host) {
        return host == null ? null : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    // ------------------------------------------------------------ busca cercada

    private record Baixado(byte[] corpo, @Nullable Charset charset, URI endereco) {
    }

    /**
     * Segue até {@link #MAXIMO_REDIRECIONAMENTOS} redirecionamentos, conferindo cada endereço. Vazio se a
     * resposta não servir: status diferente de 200, tipo errado ou corpo grande demais.
     */
    private Optional<Baixado> baixar(URI inicio, int limite, Instant prazo, boolean pagina) throws IOException {
        URI atual = inicio;
        for (int salto = 0; salto <= MAXIMO_REDIRECIONAMENTOS; salto++) {
            garantirPermitido(atual);
            HttpGet pedido = new HttpGet(atual);
            pedido.setHeader(HttpHeaders.ACCEPT, pagina ? "text/html,application/xhtml+xml" : "image/jpeg,image/png");
            pedido.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR,pt;q=0.9,en;q=0.5");
            ClassicHttpResponse resposta = cliente.executeOpen(null, pedido, null);
            try {
                int status = resposta.getCode();
                if (REDIRECIONAMENTOS.contains(status)) {
                    Header destino = resposta.getFirstHeader(HttpHeaders.LOCATION);
                    if (destino == null) {
                        return Optional.empty();
                    }
                    atual = atual.resolve(destino.getValue().strip());
                    continue;
                }
                HttpEntity entidade = resposta.getEntity();
                if (status != 200 || entidade == null) {
                    return Optional.empty();
                }
                ContentType tipo = ContentType.parseLenient(entidade.getContentType());
                String mime = tipo == null ? "" : tipo.getMimeType().toLowerCase(Locale.ROOT);
                boolean aceito = pagina
                        ? mime.equals("text/html") || mime.equals("application/xhtml+xml")
                        : IMAGENS_ACEITAS.contains(mime);
                if (!aceito || entidade.getContentLength() > limite) {
                    return Optional.empty();
                }
                byte[] corpo = lerAte(entidade.getContent(), limite, prazo);
                return corpo == null
                        ? Optional.empty()
                        : Optional.of(new Baixado(corpo, tipo == null ? null : tipo.getCharset(), atual));
            } finally {
                // Cancelar antes de fechar evita que o cliente leia o resto do corpo para reaproveitar a conexão.
                pedido.cancel();
                resposta.close();
            }
        }
        return Optional.empty();
    }

    /** Os testes ({@code permitirRedeLocal}) sobem um servidor numa porta qualquer; em produção, só 80 e 443. */
    private void garantirPermitido(URI endereco) {
        String esquema = endereco.getScheme() == null ? "" : endereco.getScheme().toLowerCase(Locale.ROOT);
        int porta = endereco.getPort();
        boolean portaPadrao = porta == -1 || permitirRedeLocal
                || (esquema.equals("http") && porta == 80)
                || (esquema.equals("https") && porta == 443);
        if (!(esquema.equals("http") || esquema.equals("https")) || endereco.getHost() == null
                || endereco.getUserInfo() != null || !portaPadrao) {
            throw new IllegalArgumentException("Endereço não permitido para prévia: " + endereco);
        }
    }

    /** Lê até {@code limite} bytes; {@code null} se passar do limite ou do prazo. */
    private static byte @Nullable [] lerAte(InputStream entrada, int limite, Instant prazo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] bloco = new byte[16 * 1024];
        for (int lidos; (lidos = entrada.read(bloco)) != -1; ) {
            if (saida.size() + lidos > limite || Instant.now().isAfter(prazo)) {
                return null;
            }
            saida.write(bloco, 0, lidos);
        }
        return saida.toByteArray();
    }
}
