package br.com.campusconnect.marketplace.model;

import br.com.campusconnect.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "categoria_anuncio")
public class CategoriaAnuncio extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private CategoriaAnuncio categoriaPai;

    @NonNull
    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @NonNull
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;
}
