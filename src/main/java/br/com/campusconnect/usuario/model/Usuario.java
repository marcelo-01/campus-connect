package br.com.campusconnect.usuario.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadeAuditada {

    @NonNull
    @Column(name = "username", nullable = false, length = 30)
    private String username;

    @NonNull
    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    @Column(name = "nome_exibicao", length = 60)
    private String nomeExibicao;

    @NonNull
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @NonNull
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "foto_perfil_url", columnDefinition = "text")
    private String fotoPerfilUrl;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusUsuario status;

    @Column(name = "ultimo_acesso_em")
    private Instant ultimoAcessoEm;

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    public Usuario(@NonNull String username, @NonNull String nomeCompleto, @NonNull String email,
            @NonNull String senhaHash, @NonNull StatusUsuario status) {
        this.username = username;
        this.nomeCompleto = nomeCompleto;
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.status = status;
    }

    public void setEmail(@NonNull String email) {
        this.email = normalizarEmail(email);
    }

    /** Use também ao buscar por e-mail: o banco só aceita esta forma ({@code ck_usuario_email_normalizado}). */
    public static String normalizarEmail(@NonNull String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
