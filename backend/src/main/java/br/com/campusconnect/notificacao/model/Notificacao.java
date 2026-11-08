package br.com.campusconnect.notificacao.model;

import br.com.campusconnect.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "notificacao")
public class Notificacao extends EntidadeBase {

    @NonNull
    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    @Column(name = "origem_id")
    private UUID origemId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNotificacao tipo;

    /** Aponta para entidades diferentes conforme o {@link #tipo}, por isso não tem FK. */
    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "mensagem", length = 255)
    private String mensagem;

    @Column(name = "lida_em")
    private Instant lidaEm;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
