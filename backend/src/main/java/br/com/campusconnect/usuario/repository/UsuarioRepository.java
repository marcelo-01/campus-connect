package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Os parâmetros de e-mail e username precisam chegar já normalizados (ver {@link Usuario}). */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
