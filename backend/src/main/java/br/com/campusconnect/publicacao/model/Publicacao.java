package br.com.campusconnect.publicacao.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "publicacao")
public class Publicacao extends EntidadeAuditada {

    @NonNull
    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @NonNull
    @Column(name = "bolha_id", nullable = false)
    private UUID bolhaId;

    @NonNull
    @Column(name = "conteudo", nullable = false, columnDefinition = "text")
    private String conteudo;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPublicacao status;

    @Column(name = "fixada", nullable = false)
    private boolean fixada;

    @Column(name = "removido_em")
    private Instant removidoEm;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "publicacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<MidiaPublicacao> midias = new ArrayList<>();

    public MidiaPublicacao adicionarMidia(TipoMidia tipo, String url) {
        MidiaPublicacao midia = new MidiaPublicacao(this, tipo, url);
        midia.setOrdem((short) midias.size());
        midias.add(midia);
        return midia;
    }
}
