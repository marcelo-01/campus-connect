package br.com.campusconnect.marketplace.model;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "anuncio_bolha")
public class AnuncioBolha {

    @EmbeddedId
    private AnuncioBolhaId id;

    @MapsId("anuncioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anuncio_id", nullable = false)
    private Anuncio anuncio;

    @CreationTimestamp
    @Column(name = "vinculado_em", nullable = false, updatable = false)
    private Instant vinculadoEm;

    public AnuncioBolha(@NonNull Anuncio anuncio, @NonNull UUID bolhaId) {
        this.id = new AnuncioBolhaId(anuncio.getId(), bolhaId);
        this.anuncio = anuncio;
    }
}
