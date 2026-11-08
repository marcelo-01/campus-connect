package br.com.campusconnect.conversa.model;

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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "participante_conversa")
public class ParticipanteConversa {

    @EmbeddedId
    private ParticipanteConversaId id;

    @MapsId("conversaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private Conversa conversa;

    @CreationTimestamp
    @Column(name = "entrou_em", nullable = false, updatable = false)
    private Instant entrouEm;

    @Setter
    @Column(name = "ultima_leitura_em")
    private Instant ultimaLeituraEm;

    @Setter
    @Column(name = "arquivada", nullable = false)
    private boolean arquivada;

    public ParticipanteConversa(@NonNull Conversa conversa, @NonNull UUID usuarioId) {
        this.id = new ParticipanteConversaId(conversa.getId(), usuarioId);
        this.conversa = conversa;
    }
}
