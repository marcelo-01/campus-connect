package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Normalização dos campos livres do perfil: link e habilidades. */
final class CamposPerfil {

    static final int MAXIMO_HABILIDADES = 15;
    static final int TAMANHO_HABILIDADE = 30;
    static final int TAMANHO_LINK = 200;

    private static final Pattern COM_ESQUEMA = Pattern.compile("(?i)^https?://.*");

    private CamposPerfil() {
    }

    /**
     * Aceita "github.com/ana" e devolve "https://github.com/ana". Só vale endereço web: qualquer outro
     * esquema (como {@code javascript:}) vira um host sem ponto e é recusado.
     */
    static @Nullable String normalizarLink(@Nullable String link) {
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
            host = new URI(valor).getHost();
        } catch (URISyntaxException invalido) {
            host = null;
        }
        if (host == null || !host.contains(".") || host.startsWith(".") || host.endsWith(".")) {
            throw new CampoInvalidoException("link", "Informe um endereço web, como github.com/seu-usuario.");
        }
        return valor;
    }

    /** Tira espaços, descarta vazias e repetidas (sem diferenciar maiúsculas) e mantém a ordem. */
    static List<String> normalizarHabilidades(@Nullable List<String> habilidades) {
        List<String> resultado = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (String habilidade : habilidades == null ? List.<String>of() : habilidades) {
            String nome = habilidade == null ? "" : habilidade.strip();
            if (nome.isEmpty() || !vistas.add(nome.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (nome.length() > TAMANHO_HABILIDADE) {
                throw new CampoInvalidoException("habilidades",
                        "Cada habilidade pode ter até " + TAMANHO_HABILIDADE + " caracteres.");
            }
            resultado.add(nome);
        }
        if (resultado.size() > MAXIMO_HABILIDADES) {
            throw new CampoInvalidoException("habilidades", "Use no máximo " + MAXIMO_HABILIDADES + " habilidades.");
        }
        return resultado;
    }

    static @Nullable String vazioParaNulo(@Nullable String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
