package br.com.campusconnect.conexao.model;

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

/** O par de usuários é ordenado pelo banco; crie conexões sempre por {@link #solicitar}. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conexao")
public class Conexao extends EntidadeBase {

    @Column(name = "usuario_menor_id", nullable = false)
    private UUID usuarioMenorId;

    @Column(name = "usuario_maior_id", nullable = false)
    private UUID usuarioMaiorId;

    @Column(name = "solicitante_id", nullable = false)
    private UUID solicitanteId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusConexao status;

    @CreationTimestamp
    @Column(name = "solicitado_em", nullable = false, updatable = false)
    private Instant solicitadoEm;

    @Setter
    @Column(name = "respondido_em")
    private Instant respondidoEm;

    public static Conexao solicitar(UUID solicitanteId, UUID destinatarioId) {
        boolean solicitanteVemAntes = OrdemUuid.vemAntes(solicitanteId, destinatarioId);

        Conexao conexao = new Conexao();
        conexao.usuarioMenorId = solicitanteVemAntes ? solicitanteId : destinatarioId;
        conexao.usuarioMaiorId = solicitanteVemAntes ? destinatarioId : solicitanteId;
        conexao.solicitanteId = solicitanteId;
        conexao.status = StatusConexao.PENDENTE;
        return conexao;
    }
}
