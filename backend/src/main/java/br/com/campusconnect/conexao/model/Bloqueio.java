package br.com.campusconnect.conexao.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uma pessoa bloqueou outra. Cada lado bloqueia por conta própria: se as duas se bloqueiam, são dois registros, e
 * desbloquear de um lado não desfaz o bloqueio do outro. A aplicação grava por SQL ({@code BloqueioRepository}).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "bloqueio")
public class Bloqueio {

    @EmbeddedId
    private BloqueioId id;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Bloqueio(@NonNull UUID bloqueadorId, @NonNull UUID bloqueadoId) {
        if (bloqueadorId.equals(bloqueadoId)) {
            throw new IllegalArgumentException("Uma conta não pode bloquear a si mesma.");
        }
        this.id = new BloqueioId(bloqueadorId, bloqueadoId);
    }
}
