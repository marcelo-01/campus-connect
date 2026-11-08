package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Trava a linha até o fim da transação. Sem isso, duas renovações simultâneas com o mesmo token
     * passariam pela checagem e criariam dois tokens válidos na mesma família.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** A família ainda tem um token que vale? Depois de logout ou de reuso detectado, não tem. */
    boolean existsByFamiliaIdAndRevogadoEmIsNullAndExpiraEmAfter(UUID familiaId, Instant agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revogadoEm = :agora
            where t.familiaId = :familiaId and t.revogadoEm is null
            """)
    int revogarFamilia(@Param("familiaId") UUID familiaId, @Param("agora") Instant agora);
}
