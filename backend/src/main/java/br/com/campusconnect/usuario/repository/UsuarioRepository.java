package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Os parâmetros de e-mail e username precisam chegar já normalizados (ver {@link Usuario}). */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    /** Já traz as habilidades: quem busca pelo username quer montar o perfil. */
    @EntityGraph(attributePaths = "habilidades")
    Optional<Usuario> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);
}
