package br.com.campusconnect.publicacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uma publicação que alguém republicou: ela passa a aparecer também no perfil dessa pessoa. As gravações do dia a dia são feitas em SQL pelo {@code InteracaoRepository}, que precisa de
 * inserção idempotente; a entidade documenta a tabela e é conferida pelo Hibernate na subida.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "republicacao")
public class Republicacao {

    @EmbeddedId
    private RepublicacaoId id;

    @MapsId("publicacaoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacao_id", nullable = false)
    private Publicacao publicacao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Republicacao(@NonNull UUID usuarioId, @NonNull Publicacao publicacao) {
        this.id = new RepublicacaoId(usuarioId, publicacao.getId());
        this.publicacao = publicacao;
    }
}
