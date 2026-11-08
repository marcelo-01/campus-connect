package br.com.campusconnect.usuario.model;

import br.com.campusconnect.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Credencial longa que troca um JWT de acesso vencido por um novo.
 *
 * <p>Só o hash do token é gravado. Cada uso revoga este registro e cria outro na mesma
 * {@link #familiaId}; assim, se um token já trocado for usado de novo, dá para derrubar a família toda.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends EntidadeBase {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NonNull
    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @NonNull
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "persistente", nullable = false)
    private boolean persistente;

    @NonNull
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RefreshToken(@NonNull Usuario usuario, @NonNull UUID familiaId, @NonNull String tokenHash,
            boolean persistente, @NonNull Instant expiraEm) {
        this.usuario = usuario;
        this.familiaId = familiaId;
        this.tokenHash = tokenHash;
        this.persistente = persistente;
        this.expiraEm = expiraEm;
    }

    public boolean isRevogado() {
        return revogadoEm != null;
    }

    public boolean expirouEm(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }
}
