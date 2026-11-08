package br.com.campusconnect.marketplace.model;

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
public class AnuncioBolhaId implements Serializable {

    @Column(name = "anuncio_id")
    private UUID anuncioId;

    @Column(name = "bolha_id")
    private UUID bolhaId;
}
