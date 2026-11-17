package br.com.campusconnect.conexao.model;

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
public class BloqueioId implements Serializable {

    @Column(name = "bloqueador_id")
    private UUID bloqueadorId;

    @Column(name = "bloqueado_id")
    private UUID bloqueadoId;
}
