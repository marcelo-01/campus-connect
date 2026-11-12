package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Limites e permissões das publicações e dos comentários. */
final class RegrasPublicacao {

    static final int TAMANHO_TEXTO = 3000;
    static final int TAMANHO_COMENTARIO = 1000;
    static final int TAMANHO_LINK = 2000;
    static final int MAXIMO_IMAGENS = 4;

    static final String ENTRE_NA_BOLHA = "Entre na bolha para interagir com as publicações dela.";
    static final String SO_QUEM_ESCREVEU_EDITA = "Só quem escreveu pode editar a publicação.";
    static final String SO_QUEM_ESCREVEU_EXCLUI = "Só quem escreveu ou um administrador pode excluir.";
    static final String NAO_REPUBLICA_O_PROPRIO = "Você não pode republicar a própria publicação.";

    private static final Pattern COM_ESQUEMA = Pattern.compile("(?i)^https?://.*");

    private RegrasPublicacao() {
    }

    /** Texto sem espaços nas pontas; nulo vira vazio, porque o post pode ter só mídia. */
    static String texto(@Nullable String conteudo) {
        String texto = conteudo == null ? "" : conteudo.strip();
        if (texto.length() > TAMANHO_TEXTO) {
            throw new CampoInvalidoException("conteudo",
                    "A publicação pode ter até " + TAMANHO_TEXTO + " caracteres.");
        }
        return texto;
    }

    /** Um post precisa de alguma coisa: texto, imagem, vídeo ou link. */
    static void garantirConteudo(String texto, int imagens, boolean video, boolean link) {
        if (imagens > MAXIMO_IMAGENS) {
            throw new CampoInvalidoException("imagens", "Anexe até " + MAXIMO_IMAGENS + " imagens.");
        }
        if (imagens > 0 && video) {
            throw new CampoInvalidoException("video", "Anexe imagens ou um vídeo, não os dois.");
        }
        if (texto.isEmpty() && imagens == 0 && !video && !link) {
            throw new CampoInvalidoException("conteudo", "Escreva algo ou anexe uma mídia.");
        }
    }

    /**
     * Endereço web com esquema. Sem esquema, vale {@code https://}; um esquema que não é web (como
     * {@code javascript:}) vira um host sem ponto e é recusado.
     */
    static @Nullable String link(@Nullable String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String valor = link.strip();
        if (!COM_ESQUEMA.matcher(valor).matches()) {
            valor = "https://" + valor;
        }
        if (valor.length() > TAMANHO_LINK) {
            throw new CampoInvalidoException("link", "O link pode ter até " + TAMANHO_LINK + " caracteres.");
        }
        String host;
        try {
            URI uri = new URI(valor);
            host = uri.getHost();
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!esquema.equals("http") && !esquema.equals("https")) || uri.getUserInfo() != null) {
                host = null;
            }
        } catch (URISyntaxException invalido) {
            host = null;
        }
        if (host == null || !host.contains(".") || host.startsWith(".") || host.endsWith(".")) {
            throw new CampoInvalidoException("link", "Informe um endereço web, como exemplo.com/pagina.");
        }
        return valor;
    }

    static String comentario(@Nullable String conteudo) {
        String texto = conteudo == null ? "" : conteudo.strip();
        if (texto.isEmpty()) {
            throw new CampoInvalidoException("conteudo", "Escreva o comentário.");
        }
        if (texto.length() > TAMANHO_COMENTARIO) {
            throw new CampoInvalidoException("conteudo",
                    "O comentário pode ter até " + TAMANHO_COMENTARIO + " caracteres.");
        }
        return texto;
    }

    static void garantirMembro(boolean membro) {
        if (!membro) {
            throw new AcessoNegadoException(ENTRE_NA_BOLHA);
        }
    }
}
