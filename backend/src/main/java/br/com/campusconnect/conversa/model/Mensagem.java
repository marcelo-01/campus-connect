package br.com.campusconnect.conversa.model;

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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Precisa de conteúdo, anexo ou ambos (CHECK {@code ck_mensagem_conteudo}). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Entity
@Table(name = "mensagem")
public class Mensagem extends EntidadeBase {

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private Conversa conversa;

    @NonNull
    @Column(name = "remetente_id", nullable = false)
    private UUID remetenteId;

    @Column(name = "conteudo", columnDefinition = "text")
    private String conteudo;

    @Column(name = "anexo_url", columnDefinition = "text")
    private String anexoUrl;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(name = "enviado_em", nullable = false, updatable = false)
    private Instant enviadoEm;

    @Column(name = "editado_em")
    private Instant editadoEm;

    @Column(name = "removido_em")
    private Instant removidoEm;
}
