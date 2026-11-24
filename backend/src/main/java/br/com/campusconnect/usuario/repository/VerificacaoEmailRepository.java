package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.VerificacaoEmail;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificacaoEmailRepository extends JpaRepository<VerificacaoEmail, UUID> {

    /**
     * O código que vale agora, se ainda valer. Trava a linha: duas tentativas ao mesmo tempo não podem contar como
     * uma só.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificacaoEmail> findFirstByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    /** Os códigos mandados desde o instante, do mais novo para o mais antigo. Servem ao limite de reenvio. */
    List<VerificacaoEmail> findByUsuarioIdAndCriadoEmAfterOrderByCriadoEmDesc(UUID usuarioId, Instant desde);

    @Modifying
    @Query("delete from VerificacaoEmail v where v.usuario.id = :usuarioId")
    void apagarDaConta(@Param("usuarioId") UUID usuarioId);
}
