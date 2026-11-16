package br.com.campusconnect.marketplace.service;

import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.marketplace.model.ModalidadePreco;
import br.com.campusconnect.marketplace.model.TipoAnuncio;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Limites e permissões dos anúncios e das avaliações. */
final class RegrasAnuncio {

    static final int TAMANHO_MINIMO_TITULO = 3;
    static final int TAMANHO_TITULO = 120;
    static final int TAMANHO_DESCRICAO = 3000;
    static final int TAMANHO_COMENTARIO = 500;
    static final int TAMANHO_BUSCA = 100;
    static final int MAXIMO_FOTOS = 6;
    static final int MAXIMO_BOLHAS = 5;
    static final BigDecimal PRECO_MINIMO = new BigDecimal("0.01");
    static final BigDecimal PRECO_MAXIMO = new BigDecimal("1000000.00");

    static final String ENTRE_NA_BOLHA = "Entre na bolha para anunciar nela.";
    static final String SO_QUEM_ANUNCIOU_ALTERA = "Só quem anunciou pode alterar o anúncio.";
    static final String SO_QUEM_ANUNCIOU_EXCLUI = "Só quem anunciou ou um administrador pode excluir o anúncio.";
    static final String NAO_AVALIA_O_PROPRIO = "Você não pode avaliar o próprio anúncio.";
    static final String SO_QUEM_AVALIOU_APAGA = "Só quem avaliou ou um administrador pode apagar a avaliação.";

    private RegrasAnuncio() {
    }

    static TipoAnuncio tipo(@Nullable String tipo) {
        TipoAnuncio valor = enumOuNulo(TipoAnuncio.class, tipo);
        if (valor == null) {
            throw new CampoInvalidoException("tipo", "Escolha se é um serviço ou um produto.");
        }
        return valor;
    }

    /** O filtro de tipo da vitrine: vazio não filtra. */
    static @Nullable TipoAnuncio filtroDeTipo(@Nullable String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        TipoAnuncio valor = enumOuNulo(TipoAnuncio.class, tipo);
        if (valor == null) {
            throw new CampoInvalidoException("tipo", "Filtre por serviço ou por produto.");
        }
        return valor;
    }

    static String titulo(@Nullable String titulo) {
        String texto = titulo == null ? "" : titulo.strip();
        if (texto.length() < TAMANHO_MINIMO_TITULO) {
            throw new CampoInvalidoException("titulo",
                    "O título precisa de pelo menos " + TAMANHO_MINIMO_TITULO + " caracteres.");
        }
        if (texto.length() > TAMANHO_TITULO) {
            throw new CampoInvalidoException("titulo", "O título pode ter até " + TAMANHO_TITULO + " caracteres.");
        }
        return texto;
    }

    static String descricao(@Nullable String descricao) {
        String texto = descricao == null ? "" : descricao.strip();
        if (texto.isEmpty()) {
            throw new CampoInvalidoException("descricao", "Descreva o que você oferece.");
        }
        if (texto.length() > TAMANHO_DESCRICAO) {
            throw new CampoInvalidoException("descricao",
                    "A descrição pode ter até " + TAMANHO_DESCRICAO + " caracteres.");
        }
        return texto;
    }

    static ModalidadePreco modalidade(@Nullable String modalidade) {
        ModalidadePreco valor = enumOuNulo(ModalidadePreco.class, modalidade);
        if (valor == null) {
            throw new CampoInvalidoException("modalidadePreco", "Escolha como você cobra.");
        }
        return valor;
    }

    /** Preço fixo e por hora pedem um valor; "A combinar" e "Grátis" ficam sem. */
    static @Nullable BigDecimal preco(ModalidadePreco modalidade, @Nullable BigDecimal preco) {
        boolean pedePreco = modalidade == ModalidadePreco.FIXO || modalidade == ModalidadePreco.POR_HORA;
        if (!pedePreco) {
            if (preco != null) {
                throw new CampoInvalidoException("preco", "Deixe o preço em branco em \"A combinar\" e \"Grátis\".");
            }
            return null;
        }
        if (preco == null) {
            throw new CampoInvalidoException("preco", "Informe o preço.");
        }
        if (preco.compareTo(PRECO_MINIMO) < 0) {
            throw new CampoInvalidoException("preco", "O preço precisa ser maior que zero.");
        }
        if (preco.compareTo(PRECO_MAXIMO) > 0) {
            throw new CampoInvalidoException("preco", "O preço pode ser de até R$ 1.000.000,00.");
        }
        if (preco.stripTrailingZeros().scale() > 2) {
            throw new CampoInvalidoException("preco", "Use no máximo 2 casas decimais.");
        }
        return preco;
    }

    /** As bolhas sem repetição, na ordem em que vieram. */
    static Set<UUID> bolhas(@Nullable List<UUID> bolhaIds) {
        Set<UUID> bolhas = new LinkedHashSet<>();
        if (bolhaIds != null) {
            bolhaIds.stream().filter(Objects::nonNull).forEach(bolhas::add);
        }
        if (bolhas.isEmpty()) {
            throw new CampoInvalidoException("bolhaIds", "Escolha pelo menos uma bolha.");
        }
        if (bolhas.size() > MAXIMO_BOLHAS) {
            throw new CampoInvalidoException("bolhaIds", "Escolha até " + MAXIMO_BOLHAS + " bolhas.");
        }
        return bolhas;
    }

    static void garantirMembro(boolean membro) {
        if (!membro) {
            throw new AcessoNegadoException(ENTRE_NA_BOLHA);
        }
    }

    static void garantirFotos(int total) {
        if (total > MAXIMO_FOTOS) {
            throw new CampoInvalidoException("imagens", "Anexe até " + MAXIMO_FOTOS + " fotos.");
        }
    }

    /**
     * As fotos que ficam na edição. Só valem fotos que o anúncio já tem: assim ninguém faz o anúncio
     * "manter", e depois apagar do disco, o arquivo de outra pessoa.
     */
    static List<String> mantidas(@Nullable List<String> pedidas, List<String> atuais) {
        if (pedidas == null) {
            return List.of();
        }
        Set<String> vistas = new HashSet<>();
        for (String url : pedidas) {
            if (url == null || !atuais.contains(url) || !vistas.add(url)) {
                throw new CampoInvalidoException("imagensMantidas", "As fotos mantidas precisam ser deste anúncio.");
            }
        }
        return List.copyOf(pedidas);
    }

    static short nota(@Nullable Integer nota) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new CampoInvalidoException("nota", "Dê uma nota de 1 a 5.");
        }
        return nota.shortValue();
    }

    static @Nullable String comentario(@Nullable String comentario) {
        String texto = comentario == null ? "" : comentario.strip();
        if (texto.length() > TAMANHO_COMENTARIO) {
            throw new CampoInvalidoException("comentario",
                    "O comentário pode ter até " + TAMANHO_COMENTARIO + " caracteres.");
        }
        return texto.isEmpty() ? null : texto;
    }

    /** O texto procurado na vitrine; vazio não filtra. */
    static @Nullable String busca(@Nullable String busca) {
        String texto = busca == null ? "" : busca.strip();
        if (texto.length() > TAMANHO_BUSCA) {
            throw new CampoInvalidoException("q", "A busca pode ter até " + TAMANHO_BUSCA + " caracteres.");
        }
        return texto.isEmpty() ? null : texto;
    }

    private static <E extends Enum<E>> @Nullable E enumOuNulo(Class<E> tipo, @Nullable String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalido) {
            return null;
        }
    }
}
