package br.com.campusconnect.bolha.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "membro_bolha")
public class MembroBolha {

    @EmbeddedId
    private MembroBolhaId id;

    @MapsId("bolhaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bolha_id", nullable = false)
    private Bolha bolha;

    @Setter
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private PapelMembro papel;

    @Setter
    @Column(name = "notificacoes_ativas", nullable = false)
    private boolean notificacoesAtivas = true;

    @CreationTimestamp
    @Column(name = "entrou_em", nullable = false, updatable = false)
    private Instant entrouEm;

    public MembroBolha(@NonNull Bolha bolha, @NonNull UUID usuarioId, @NonNull PapelMembro papel) {
        this.id = new MembroBolhaId(bolha.getId(), usuarioId);
        this.bolha = bolha;
        this.papel = papel;
    }
}
