package br.com.campusconnect.bolha.model;

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
public class MembroBolhaId implements Serializable {

    @Column(name = "bolha_id")
    private UUID bolhaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;
}
