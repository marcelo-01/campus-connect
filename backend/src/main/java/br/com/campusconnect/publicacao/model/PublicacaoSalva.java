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
 * Uma publicação que alguém salvou. Só quem salvou vê a lista. As gravações do dia a dia são feitas em SQL pelo {@code InteracaoRepository}, que precisa de
 * inserção idempotente; a entidade documenta a tabela e é conferida pelo Hibernate na subida.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "publicacao_salva")
public class PublicacaoSalva {

    @EmbeddedId
    private PublicacaoSalvaId id;

    @MapsId("publicacaoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacao_id", nullable = false)
    private Publicacao publicacao;

    @CreationTimestamp
    @Column(name = "salva_em", nullable = false, updatable = false)
    private Instant salvaEm;

    public PublicacaoSalva(@NonNull UUID usuarioId, @NonNull Publicacao publicacao) {
        this.id = new PublicacaoSalvaId(usuarioId, publicacao.getId());
        this.publicacao = publicacao;
    }
}
