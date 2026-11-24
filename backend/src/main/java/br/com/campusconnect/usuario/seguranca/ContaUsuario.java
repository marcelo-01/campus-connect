package br.com.campusconnect.usuario.seguranca;

import br.com.campusconnect.usuario.model.StatusUsuario;
import br.com.campusconnect.usuario.model.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A conta como o Spring Security a enxerga durante o login. Só {@code ATIVO} e não anonimizada pode entrar, e o
 * e-mail precisa estar verificado (conferido em {@code SegurancaConfig#gerenciadorAutenticacao}).
 */
@Getter
public class ContaUsuario implements UserDetails, CredentialsContainer {

    private final UUID id;
    private final String email;
    private final StatusUsuario status;
    private final boolean ativa;
    private final boolean emailVerificado;
    private String senhaHash;

    ContaUsuario(Usuario usuario) {
        this.id = usuario.getId();
        this.email = usuario.getEmail();
        this.status = usuario.getStatus();
        this.ativa = usuario.estaAtiva();
        this.emailVerificado = usuario.isEmailVerificado();
        this.senhaHash = usuario.getSenhaHash();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != StatusUsuario.SUSPENSO && status != StatusUsuario.BANIDO;
    }

    /**
     * Suspensa ou banida aparece como bloqueada; desativada ou anonimizada, como desabilitada. O e-mail não verificado
     * fica de fora: ele tem resposta própria.
     */
    @Override
    public boolean isEnabled() {
        return ativa;
    }

    @Override
    public void eraseCredentials() {
        senhaHash = null;
    }
}
