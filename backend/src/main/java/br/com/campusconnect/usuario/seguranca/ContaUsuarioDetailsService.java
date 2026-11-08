package br.com.campusconnect.usuario.seguranca;

import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** O login é pelo e-mail. */
@Service
@RequiredArgsConstructor
class ContaUsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarios.findByEmail(Usuario.normalizarEmail(email))
                .map(ContaUsuario::new)
                .orElseThrow(() -> new UsernameNotFoundException("Nenhuma conta com este e-mail"));
    }
}
