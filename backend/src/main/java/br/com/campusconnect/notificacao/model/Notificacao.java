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

    /**
     * A página para onde a notificação leva: a publicação ou o anúncio. Aponta para entidades diferentes conforme o
     * {@link #tipo}, por isso não tem FK.
     */
    @Column(name = "referencia_id")
    private UUID referenciaId;

    /** O que foi curtido ou escrito dentro da referência: o comentário ou a avaliação. Também não tem FK. */
    @Column(name = "detalhe_id")
    private UUID detalheId;

    /** O começo do comentário, copiado quando a notificação nasce: comentários não mudam depois de escritos. */
    @Column(name = "trecho", length = 255)
    private String trecho;

    @Column(name = "lida_em")
    private Instant lidaEm;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
