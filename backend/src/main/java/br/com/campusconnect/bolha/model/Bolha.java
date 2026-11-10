package br.com.campusconnect.bolha.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Uma comunidade. A hierarquia (geral, campus, curso) vive só aqui, por {@link #bolhaPai}.
 *
 * <p>Tipo, bolha acima e slug não mudam depois de criados: mudar o tipo ou o pai obrigaria a revalidar
 * as sub-bolhas e os membros, e mudar o slug quebraria os links já compartilhados.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bolha")
public class Bolha extends EntidadeAuditada {

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoBolha tipo;

    @NonNull
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /** Trecho da URL: {@code /bolhas/<slug>}. */
    @Setter(AccessLevel.NONE)
    @Column(name = "slug", nullable = false, length = 140)
    private String slug;

    /** Texto curto que aparece na marca da bolha quando ela não tem foto, como "IFBA". */
    @Column(name = "sigla", length = 10)
    private String sigla;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /** Endereço físico, em texto livre. */
    @Column(name = "endereco", length = 200)
    private String endereco;

    /** Caminho público da foto, servida por {@code /api/arquivos}. */
    @Column(name = "foto_url", columnDefinition = "text")
    private String fotoUrl;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bolha_pai_id")
    private Bolha bolhaPai;

    /** Ainda sem uso: vai valer quando houver publicações. */
    @Column(name = "somente_leitura", nullable = false)
    private boolean somenteLeitura;

    /** @param bolhaPai bolha acima, ou {@code null} para ficar no topo */
    public Bolha(@NonNull TipoBolha tipo, @NonNull String nome, @NonNull String slug, @Nullable Bolha bolhaPai) {
        this.tipo = tipo;
        this.nome = nome;
        this.slug = slug;
        this.bolhaPai = bolhaPai;
    }
}
