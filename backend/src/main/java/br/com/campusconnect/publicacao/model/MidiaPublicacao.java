package br.com.campusconnect.publicacao.model;

import br.com.campusconnect.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "midia_publicacao")
public class MidiaPublicacao extends EntidadeBase {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacao_id", nullable = false)
    private Publicacao publicacao;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMidia tipo;

    @NonNull
    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "mime_type", length = 80)
    private String mimeType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "ordem", nullable = false)
    private Short ordem = 0;

    // Prévia de um LINK, quando o servidor conseguiu ler a página. Nulos nas outras mídias.

    @Column(name = "titulo", length = 300)
    private String titulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "site", length = 100)
    private String site;

    /** Cópia da imagem de destaque da página, gravada em {@code /api/arquivos}. */
    @Column(name = "imagem_url", columnDefinition = "text")
    private String imagemUrl;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
