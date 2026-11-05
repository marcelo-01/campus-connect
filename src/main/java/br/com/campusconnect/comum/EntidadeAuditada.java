package br.com.campusconnect.comum;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Base das tabelas que têm {@code criado_em} e {@code atualizado_em}.
 *
 * <p>{@code atualizado_em} usa {@code @PreUpdate} em vez de {@code @UpdateTimestamp}, que também
 * grava no INSERT: no schema a coluna fica NULL até a primeira alteração.
 */
@Getter
@MappedSuperclass
public abstract class EntidadeAuditada extends EntidadeBase {

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @PreUpdate
    void registrarAtualizacao() {
        atualizadoEm = Instant.now();
    }
}
