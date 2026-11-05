package br.com.campusconnect.conversa.model;

import br.com.campusconnect.comum.EntidadeBase;
import br.com.campusconnect.comum.OrdemUuid;
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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Conversa direta tem o par ordenado preenchido; em grupo, os dois ficam nulos. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversa")
public class Conversa extends EntidadeBase {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoConversa tipo;

    @Setter
    @Column(name = "titulo", length = 120)
    private String titulo;

    @Column(name = "usuario_menor_id")
    private UUID usuarioMenorId;

    @Column(name = "usuario_maior_id")
    private UUID usuarioMaiorId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public static Conversa direta(UUID umId, UUID outroId) {
        boolean umVemAntes = OrdemUuid.vemAntes(umId, outroId);

        Conversa conversa = new Conversa();
        conversa.tipo = TipoConversa.DIRETA;
        conversa.usuarioMenorId = umVemAntes ? umId : outroId;
        conversa.usuarioMaiorId = umVemAntes ? outroId : umId;
        return conversa;
    }

    public static Conversa grupo(String titulo) {
        Conversa conversa = new Conversa();
        conversa.tipo = TipoConversa.GRUPO;
        conversa.titulo = titulo;
        return conversa;
    }
}
