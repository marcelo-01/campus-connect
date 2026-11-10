package br.com.campusconnect.usuario.model;

import br.com.campusconnect.comum.EntidadeAuditada;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "capa_url", columnDefinition = "text")
    private String capaUrl;

    /** Sempre com {@code https://} ou {@code http://} na frente. */
    @Column(name = "link", length = 200)
    private String link;

    /** Na ordem em que a pessoa digitou. Troque a lista inteira com {@link #setHabilidades}. */
    @Setter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "habilidade_usuario", joinColumns = @JoinColumn(name = "usuario_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "nome", nullable = false, length = 30)
    private List<String> habilidades = new ArrayList<>();

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

    /** Pode criar, editar e excluir bolhas. Só muda por SQL (ver a migration V16). */
    @Setter(AccessLevel.NONE)
    @Column(name = "administrador", nullable = false)
    private boolean administrador;

    public Usuario(@NonNull String username, @NonNull String nomeCompleto, @NonNull String email,
            @NonNull String senhaHash, @NonNull StatusUsuario status) {
        this.username = normalizarUsername(username);
        this.nomeCompleto = nomeCompleto;
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.status = status;
    }

    /** Só conta ativa e não anonimizada pode entrar ou renovar a sessão. */
    public boolean podeEntrar() {
        return status == StatusUsuario.ATIVO && anonimizadoEm == null;
    }

    /** Conta suspensa ou desativada perde os poderes de administrador junto com o acesso. */
    public boolean podeAdministrar() {
        return administrador && podeEntrar();
    }

    /**
     * Troca a lista por uma nova em vez de alterar a atual: assim o Hibernate apaga as linhas antigas
     * antes de inserir as novas, e o índice único de nome sem diferenciar maiúsculas
     * ({@code ux_habilidade_usuario_nome}) nunca vê dois nomes iguais no meio da troca.
     */
    public void setHabilidades(@NonNull List<String> habilidades) {
        this.habilidades = new ArrayList<>(habilidades);
    }

    public void setUsername(@NonNull String username) {
        this.username = normalizarUsername(username);
    }

    public void setEmail(@NonNull String email) {
        this.email = normalizarEmail(email);
    }

    /** Use também ao buscar por e-mail: o banco só aceita esta forma ({@code ck_usuario_email_normalizado}). */
    public static String normalizarEmail(@NonNull String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Use também ao buscar por username: o banco só aceita esta forma
     * ({@code ck_usuario_username_normalizado}). O {@code @} da frente é só o jeito de exibir e sai.
     */
    public static String normalizarUsername(@NonNull String username) {
        String semEspacos = username.strip();
        if (semEspacos.startsWith("@")) {
            semEspacos = semEspacos.substring(1);
        }
        return semEspacos.toLowerCase(Locale.ROOT);
    }
}
