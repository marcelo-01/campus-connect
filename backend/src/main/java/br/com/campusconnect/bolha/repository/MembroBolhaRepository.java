package br.com.campusconnect.bolha.repository;

import br.com.campusconnect.bolha.model.MembroBolha;
import br.com.campusconnect.bolha.model.MembroBolhaId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembroBolhaRepository extends JpaRepository<MembroBolha, MembroBolhaId> {

    @Query("select m.id.bolhaId as bolhaId, count(m) as total from MembroBolha m group by m.id.bolhaId")
    List<TotalPorBolha> contarPorBolha();

    @Query("select m.id.bolhaId from MembroBolha m where m.id.usuarioId = :usuarioId")
    Set<UUID> idsDasBolhasDo(@Param("usuarioId") UUID usuarioId);

    /** Não faz nada se a pessoa já participa: dois cliques seguidos em "Entrar" não dão erro. */
    @Modifying
    @Query(value = """
            insert into membro_bolha (bolha_id, usuario_id, papel)
            values (:bolhaId, :usuarioId, :papel)
            on conflict do nothing
            """, nativeQuery = true)
    void entrar(@Param("bolhaId") UUID bolhaId, @Param("usuarioId") UUID usuarioId, @Param("papel") String papel);

    @Modifying
    @Query("delete from MembroBolha m where m.id.usuarioId = :usuarioId and m.id.bolhaId in :bolhas")
    void sair(@Param("usuarioId") UUID usuarioId, @Param("bolhas") Collection<UUID> bolhas);

    /** Os que entraram por último primeiro. O limite segura a resposta até a lista ganhar paginação. */
    List<MembroBolha> findTop100ByIdBolhaIdOrderByEntrouEmDesc(UUID bolhaId);

    interface TotalPorBolha {

        UUID getBolhaId();

        long getTotal();
    }
}
