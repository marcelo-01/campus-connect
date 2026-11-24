package br.com.campusconnect.usuario.model;

import br.com.campusconnect.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Um código mandado para o e-mail da conta, para verificar o e-mail ou redefinir a senha. Só o mais novo da conta em
 * cada finalidade vale, e só o hash dele é gravado.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "verificacao_email")
public class VerificacaoEmail extends EntidadeBase {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "finalidade", nullable = false, length = 20)
    private FinalidadeCodigo finalidade;

    @NonNull
    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(name = "tentativas", nullable = false)
    private short tentativas;

    @NonNull
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public VerificacaoEmail(@NonNull Usuario usuario, @NonNull FinalidadeCodigo finalidade, @NonNull String codigoHash,
            @NonNull Instant expiraEm) {
        this.usuario = usuario;
        this.finalidade = finalidade;
        this.codigoHash = codigoHash;
        this.expiraEm = expiraEm;
    }

    /** Ainda no prazo e sem ter esgotado as tentativas. */
    public boolean valeEm(Instant agora, int maximoTentativas) {
        return expiraEm.isAfter(agora) && tentativas < maximoTentativas;
    }

    /** @return quantas tentativas ainda restam depois desta */
    public int registrarErro(int maximoTentativas) {
        tentativas++;
        return Math.max(0, maximoTentativas - tentativas);
    }
}
