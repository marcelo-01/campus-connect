package br.com.campusconnect.usuario.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Vínculo opcional do usuário com uma instituição.
 *
 * <p>Instituição, campus e curso são do módulo {@code instituicao}, por isso aqui ficam só os ids.
 * As FKs compostas do banco garantem que o campus pertence à instituição e o curso ao campus.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "vinculo_institucional")
public class VinculoInstitucional extends EntidadeAuditada {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NonNull
    @Column(name = "instituicao_id", nullable = false)
    private UUID instituicaoId;

    @Column(name = "campus_id")
    private UUID campusId;

    @Column(name = "curso_id")
    private UUID cursoId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoVinculo tipo;

    @Column(name = "matricula", length = 30)
    private String matricula;

    @Column(name = "verificado_em")
    private Instant verificadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;
}
