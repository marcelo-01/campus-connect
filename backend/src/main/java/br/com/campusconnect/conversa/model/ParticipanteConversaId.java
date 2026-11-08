package br.com.campusconnect.conversa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ParticipanteConversaId implements Serializable {

    @Column(name = "conversa_id")
    private UUID conversaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;
}
