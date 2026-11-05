package br.com.campusconnect.publicacao.model;

import br.com.campusconnect.comum.EntidadeAuditada;
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
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "comentario")
public class Comentario extends EntidadeAuditada {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacao_id", nullable = false)
    private Publicacao publicacao;

    @NonNull
    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comentario_pai_id")
    private Comentario comentarioPai;

    @NonNull
    @Column(name = "conteudo", nullable = false, length = 1000)
    private String conteudo;

    @Column(name = "removido_em")
    private Instant removidoEm;
}
