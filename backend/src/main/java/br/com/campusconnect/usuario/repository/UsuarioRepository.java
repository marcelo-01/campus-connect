package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.Usuario;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Os parâmetros de e-mail e username precisam chegar já normalizados (ver {@link Usuario}). */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    /** Já traz as habilidades: quem busca pelo username quer montar o perfil. */
    @EntityGraph(attributePaths = "habilidades")
    Optional<Usuario> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    /**
     * Apaga a conta que nunca verificou o e-mail, foi criada antes do limite e está com o username. A conta
     * {@code dono}, que pode manter o username, fica de fora.
     *
     * <p>É um delete direto no banco, que roda na hora. Pelo {@code EntityManager}, o Hibernate deixaria o delete
     * para depois do insert da conta nova, e o UNIQUE do username recusaria o insert.
     */
    @Modifying
    @Query("""
            delete from Usuario u
            where u.username = :username and u.emailVerificado = false and u.criadoEm < :limite
              and (:dono is null or u.id <> :dono)
            """)
    int apagarNaoVerificadaComUsername(@Param("username") String username, @Param("limite") Instant limite,
            @Param("dono") @Nullable UUID dono);

    /**
     * Entre os ids, os das contas que aparecem para os outros (as mesmas de {@link Usuario#podeEntrar()}) cujo nome
     * ou username casa com o padrão do {@code like}. Para não filtrar por texto, passe {@code "%"}.
     */
    @Query(value = """
            select id from usuario
            where id in (:ids) and status = 'ATIVO' and anonimizado_em is null and email_verificado
              and (nome_completo ilike :padrao escape '\\' or username ilike :padrao escape '\\')
            """, nativeQuery = true)
    List<UUID> idsPublicosQueCasam(@Param("ids") Collection<UUID> ids, @Param("padrao") String padrao);
}
