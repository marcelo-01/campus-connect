package br.com.campusconnect.email.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Um e-mail em duas versões, HTML e texto puro, lido de {@code resources/email}. Os marcadores {@code {{nome}}} são
 * trocados pelos valores. Na versão HTML, os valores passam por {@link HtmlUtils#htmlEscape}, porque o nome é
 * digitado pela pessoa.
 */
final class ModeloEmail {

    private final String html;
    private final String texto;

    private ModeloEmail(String html, String texto) {
        this.html = html;
        this.texto = texto;
    }

    /** @param nome o nome dos arquivos, sem a extensão: {@code verificacao} lê {@code verificacao.html} e {@code .txt} */
    static ModeloEmail ler(String nome) {
        return new ModeloEmail(arquivo("email/" + nome + ".html"), arquivo("email/" + nome + ".txt"));
    }

    String html(Map<String, String> valores) {
        String resultado = html;
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            resultado = resultado.replace("{{" + valor.getKey() + "}}", HtmlUtils.htmlEscape(valor.getValue()));
        }
        return resultado;
    }

    String texto(Map<String, String> valores) {
        String resultado = texto;
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            resultado = resultado.replace("{{" + valor.getKey() + "}}", valor.getValue());
        }
        return resultado;
    }

    private static String arquivo(String caminho) {
        try (var entrada = new ClassPathResource(caminho).getInputStream()) {
            return StreamUtils.copyToString(entrada, StandardCharsets.UTF_8);
        } catch (IOException excecao) {
            throw new UncheckedIOException("Modelo de e-mail não encontrado: " + caminho, excecao);
        }
    }
}
