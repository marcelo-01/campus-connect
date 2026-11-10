package br.com.campusconnect.bolha.service;

import br.com.campusconnect.bolha.model.Bolha;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Todas as bolhas montadas em árvore, já com o total de membros de cada uma e com o que quem está vendo
 * participa. As bolhas são criadas pela equipe e cabem em memória; quando forem muitas, a busca e a
 * paginação vão para o banco.
 */
final class Arvore {

    private final List<No> emOrdem;
    private final Map<UUID, No> porId = new HashMap<>();
    private final Map<String, No> porSlug = new HashMap<>();

    private Arvore(List<No> emOrdem) {
        this.emOrdem = emOrdem;
        for (No no : emOrdem) {
            porId.put(no.bolha.getId(), no);
            porSlug.put(no.bolha.getSlug(), no);
        }
    }

    /**
     * @param totais membros por bolha; bolha sem ninguém pode ficar de fora
     * @param deQuemVe bolhas em que quem está vendo participa
     */
    static Arvore montar(List<Bolha> bolhas, Map<UUID, Long> totais, Set<UUID> deQuemVe) {
        Collator nomes = Collator.getInstance(Locale.of("pt", "BR"));
        Comparator<Bolha> ordem = Comparator.comparing(Bolha::getTipo).thenComparing(Bolha::getNome, nomes);

        Map<UUID, List<Bolha>> filhasPorPai = new HashMap<>();
        for (Bolha bolha : bolhas) {
            filhasPorPai.computeIfAbsent(idDoPai(bolha), chave -> new ArrayList<>()).add(bolha);
        }
        filhasPorPai.values().forEach(irmas -> irmas.sort(ordem));

        List<No> emOrdem = new ArrayList<>(bolhas.size());
        visitar(null, 0, filhasPorPai, totais, deQuemVe, emOrdem);
        return new Arvore(emOrdem);
    }

    /** Pré-ordem: cada bolha logo antes das que ficam dentro dela, como no menu lateral. */
    private static void visitar(@Nullable No pai, int nivel, Map<UUID, List<Bolha>> filhasPorPai,
            Map<UUID, Long> totais, Set<UUID> deQuemVe, List<No> emOrdem) {
        UUID idDoPai = pai == null ? null : pai.bolha.getId();
        for (Bolha bolha : filhasPorPai.getOrDefault(idDoPai, List.of())) {
            No no = new No(bolha, pai, nivel, totais.getOrDefault(bolha.getId(), 0L),
                    deQuemVe.contains(bolha.getId()));
            if (pai != null) {
                pai.filhas.add(no);
            }
            emOrdem.add(no);
            visitar(no, nivel + 1, filhasPorPai, totais, deQuemVe, emOrdem);
        }
    }

    private static @Nullable UUID idDoPai(Bolha bolha) {
        // Ler o id do proxy não dispara SELECT.
        return bolha.getBolhaPai() == null ? null : bolha.getBolhaPai().getId();
    }

    List<No> emOrdem() {
        return emOrdem;
    }

    Optional<No> porId(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    Optional<No> porSlug(String slug) {
        return Optional.ofNullable(porSlug.get(slug));
    }

    static final class No {

        private final Bolha bolha;
        private final @Nullable No pai;
        private final int nivel;
        private final long totalMembros;
        private final boolean membro;
        private final List<No> filhas = new ArrayList<>();

        private No(Bolha bolha, @Nullable No pai, int nivel, long totalMembros, boolean membro) {
            this.bolha = bolha;
            this.pai = pai;
            this.nivel = nivel;
            this.totalMembros = totalMembros;
            this.membro = membro;
        }

        UUID id() {
            return bolha.getId();
        }

        BolhaResumo resumo() {
            return new BolhaResumo(bolha.getId(), bolha.getNome(), bolha.getSlug(), bolha.getSigla(),
                    bolha.getTipo().name(), bolha.getFotoUrl(), bolha.getDescricao(),
                    pai == null ? null : pai.bolha.getId(), nivel, totalMembros, membro);
        }

        BolhaDetalhe detalhe() {
            List<BolhaResumo> caminho = new ArrayList<>();
            for (No acima = pai; acima != null; acima = acima.pai) {
                caminho.addFirst(acima.resumo());
            }
            return new BolhaDetalhe(bolha.getId(), bolha.getNome(), bolha.getSlug(), bolha.getSigla(),
                    bolha.getTipo().name(), bolha.getFotoUrl(), bolha.getDescricao(), bolha.getEndereco(),
                    totalMembros, membro, bolha.getCriadoEm(), List.copyOf(caminho),
                    filhas.stream().map(No::resumo).toList());
        }
    }
}
