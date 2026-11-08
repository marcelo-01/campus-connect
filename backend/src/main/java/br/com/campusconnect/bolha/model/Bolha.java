package br.com.campusconnect.bolha.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * O {@link #tipo} define quais níveis da hierarquia são preenchidos (CHECK {@code ck_bolha_escopo}).
 *
 * <p>Como em {@code VinculoInstitucional}, as FKs compostas ficam a cargo do banco.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "bolha")
public class Bolha extends EntidadeAuditada {

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoBolha tipo;

    @NonNull
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NonNull
    @Column(name = "slug", nullable = false, length = 140)
    private String slug;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bolha_pai_id")
    private Bolha bolhaPai;

    @Column(name = "instituicao_id")
    private UUID instituicaoId;

    @Column(name = "campus_id")
    private UUID campusId;

    @Column(name = "curso_id")
    private UUID cursoId;

    @Column(name = "entrada_automatica", nullable = false)
    private boolean entradaAutomatica;

    @Column(name = "somente_leitura", nullable = false)
    private boolean somenteLeitura;
}
