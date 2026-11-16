package br.com.campusconnect.marketplace.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "anuncio")
public class Anuncio extends EntidadeAuditada {

    @NonNull
    @Column(name = "anunciante_id", nullable = false)
    private UUID anuncianteId;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaAnuncio categoria;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoAnuncio tipo;

    @NonNull
    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "preco", precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_preco", length = 20)
    private ModalidadePreco modalidadePreco;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAnuncio status;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "anuncio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<ImagemAnuncio> imagens = new ArrayList<>();

    public ImagemAnuncio adicionarImagem(String url) {
        ImagemAnuncio imagem = new ImagemAnuncio(this, url);
        imagem.setOrdem((short) imagens.size());
        imagens.add(imagem);
        return imagem;
    }

    /**
     * Troca as fotos na edição: ficam as mantidas, na ordem dada, e as novas entram no fim. A primeira é a
     * capa. A lista em memória já sai na ordem nova, porque o {@code @OrderBy} só vale ao carregar.
     *
     * @param mantidas URLs de fotos que o anúncio já tem
     * @return as URLs das fotos que saíram, para apagar do disco
     */
    public List<String> trocarImagens(List<String> mantidas, List<String> novas) {
        List<String> removidas = new ArrayList<>();
        imagens.removeIf(imagem -> {
            boolean sai = !mantidas.contains(imagem.getUrl());
            if (sai) {
                removidas.add(imagem.getUrl());
            }
            return sai;
        });
        imagens.sort(Comparator.comparingInt(imagem -> mantidas.indexOf(imagem.getUrl())));
        novas.forEach(url -> imagens.add(new ImagemAnuncio(this, url)));
        for (int ordem = 0; ordem < imagens.size(); ordem++) {
            imagens.get(ordem).setOrdem((short) ordem);
        }
        return removidas;
    }
}
