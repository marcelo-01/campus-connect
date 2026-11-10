package br.com.campusconnect.bolha.service;

import br.com.campusconnect.bolha.model.TipoBolha;
import br.com.campusconnect.bolha.repository.BolhaRepository;
import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Slug, tipo e posição na hierarquia. */
@Component
@RequiredArgsConstructor
class RegrasBolha {

    static final String TIPO_INVALIDO = "Escolha o tipo da bolha.";
    static final String PAI_NAO_EXISTE = "A bolha acima não existe mais.";
    static final String SLUG_EM_USO = "Outra bolha acabou de ser criada com um nome parecido. Tente salvar de novo.";

    /**
     * Colidiriam com rotas fixas: {@code /bolhas/nova} no frontend e {@code /api/bolhas/usuario/...} na API.
     */
    static final Set<String> SLUGS_RESERVADOS = Set.of("nova", "usuario");

    /** Sobra espaço na coluna (140) para o sufixo de desempate. */
    private static final int TAMANHO_SLUG = 120;

    private final BolhaRepository bolhas;

    /** "Análise e Desenv. de Sistemas" vira "analise-e-desenv-de-sistemas"; se já existir, "...-2". */
    String gerarSlug(String nome) {
        String base = slugBase(nome);
        String candidato = base;
        for (int sufixo = 2; SLUGS_RESERVADOS.contains(candidato) || bolhas.existsBySlug(candidato); sufixo++) {
            candidato = base + "-" + sufixo;
        }
        return candidato;
    }

    static String slugBase(String nome) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        if (slug.length() > TAMANHO_SLUG) {
            slug = slug.substring(0, TAMANHO_SLUG);
        }
        slug = slug.replaceAll("^-+|-+$", "");
        // Um nome só com símbolos ("???") não deixa nada aproveitável.
        return slug.isEmpty() ? "bolha" : slug;
    }

    static TipoBolha tipo(@Nullable String valor) {
        try {
            return TipoBolha.valueOf(valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            throw new CampoInvalidoException("tipo", TIPO_INVALIDO);
        }
    }

    /** @param pai tipo da bolha acima, ou {@code null} para ficar no topo */
    static void garantirPosicao(TipoBolha tipo, @Nullable TipoBolha pai) {
        if (tipo.podeFicarDentroDe(pai)) {
            return;
        }
        String mensagem = switch (tipo) {
            case GERAL -> "Uma bolha geral fica no topo: deixe sem bolha acima.";
            case CAMPUS -> "Um campus fica dentro de uma bolha geral.";
            case CURSO -> "Um curso fica dentro de um campus.";
            case COMUNIDADE -> "Uma comunidade não pode ficar dentro de outra comunidade.";
        };
        throw new CampoInvalidoException("bolhaPaiId", mensagem);
    }

    /** Texto opcional: sem espaços nas pontas, e vazio vira {@code null}. */
    static @Nullable String vazioParaNulo(@Nullable String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.strip();
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Duas bolhas com o mesmo nome criadas ao mesmo tempo passam pela checagem de {@link #gerarSlug}; o
     * UNIQUE do banco desempata e aqui o erro volta a apontar o campo do nome.
     */
    static RuntimeException traduzirDuplicidade(DataIntegrityViolationException excecao) {
        if ("bolha_slug_key".equals(restricao(excecao))) {
            return new CampoDuplicadoException("nome", SLUG_EM_USO);
        }
        return excecao;
    }

    /** Uma sub-bolha criada entre a checagem e a exclusão esbarra na FK da bolha acima. */
    static RuntimeException traduzirExclusao(DataIntegrityViolationException excecao) {
        if ("bolha_bolha_pai_id_fkey".equals(restricao(excecao))) {
            return new BolhaComSubBolhasException();
        }
        return excecao;
    }

    private static @Nullable String restricao(DataIntegrityViolationException excecao) {
        return excecao.getCause() instanceof ConstraintViolationException violacao
                ? violacao.getConstraintName()
                : null;
    }
}
