package br.com.campusconnect.usuario.repository;

import br.com.campusconnect.usuario.model.FinalidadeCodigo;
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
     * O código mais novo da conta nessa finalidade, que é o único que pode valer. Trava a linha: duas tentativas ao
     * mesmo tempo não podem contar como uma só.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificacaoEmail> findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(UUID usuarioId,
            FinalidadeCodigo finalidade);

    /**
     * Os códigos da finalidade mandados desde o instante, do mais novo para o mais antigo. Servem ao limite de
     * reenvio.
     */
    List<VerificacaoEmail> findByUsuarioIdAndFinalidadeAndCriadoEmAfterOrderByCriadoEmDesc(UUID usuarioId,
            FinalidadeCodigo finalidade, Instant desde);

    @Modifying
    @Query("delete from VerificacaoEmail v where v.usuario.id = :usuarioId and v.finalidade = :finalidade")
    void apagarDaConta(@Param("usuarioId") UUID usuarioId, @Param("finalidade") FinalidadeCodigo finalidade);

    /** Todos os códigos da conta, das duas finalidades. */
    @Modifying
    @Query("delete from VerificacaoEmail v where v.usuario.id = :usuarioId")
    void apagarTodosDaConta(@Param("usuarioId") UUID usuarioId);
}
