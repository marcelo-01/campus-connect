package br.com.campusconnect.moderacao.model;

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

/** Preencha exatamente um alvo: publicação, comentário, anúncio ou usuário (CHECK {@code ck_denuncia_alvo}). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "denuncia")
public class Denuncia extends EntidadeBase {

    @NonNull
    @Column(name = "denunciante_id", nullable = false)
    private UUID denuncianteId;

    @Column(name = "publicacao_id")
    private UUID publicacaoId;

    @Column(name = "comentario_id")
    private UUID comentarioId;

    @Column(name = "anuncio_id")
    private UUID anuncioId;

    @Column(name = "usuario_alvo_id")
    private UUID usuarioAlvoId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 30)
    private MotivoDenuncia motivo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusDenuncia status;

    @Column(name = "moderador_id")
    private UUID moderadorId;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
