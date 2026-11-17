package br.com.campusconnect.conexao.repository;

import br.com.campusconnect.conexao.model.Bloqueio;
import br.com.campusconnect.conexao.model.BloqueioId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Cada escrita é uma instrução só, como no {@link ConexaoRepository}; repetir uma ação não muda nada. */
public interface BloqueioRepository extends JpaRepository<Bloqueio, BloqueioId> {

    @Modifying
    @Query(value = """
            insert into bloqueio (bloqueador_id, bloqueado_id) values (:quem, :outro)
            on conflict (bloqueador_id, bloqueado_id) do nothing
            """, nativeQuery = true)
    int bloquear(@Param("quem") UUID quem, @Param("outro") UUID outro);

    @Modifying
    @Query(value = "delete from bloqueio where bloqueador_id = :quem and bloqueado_id = :outro", nativeQuery = true)
    int desbloquear(@Param("quem") UUID quem, @Param("outro") UUID outro);

    /** Se {@code quem} bloqueou {@code outro}. O contrário não conta. */
    @Query("select count(b) > 0 from Bloqueio b where b.id.bloqueadorId = :quem and b.id.bloqueadoId = :outro")
    boolean bloqueou(@Param("quem") UUID quem, @Param("outro") UUID outro);

    /** Se há bloqueio entre as duas pessoas, em qualquer sentido. */
    @Query("""
            select count(b) > 0 from Bloqueio b
            where (b.id.bloqueadorId = :a and b.id.bloqueadoId = :b) or (b.id.bloqueadorId = :b and b.id.bloqueadoId = :a)
            """)
    boolean existeEntre(@Param("a") UUID a, @Param("b") UUID b);

    @Query("select b from Bloqueio b where b.id.bloqueadorId = :quem")
    List<Bloqueio> feitosPor(@Param("quem") UUID quem);
}
