package br.com.campusconnect.bolha.repository;

import br.com.campusconnect.bolha.model.MembroBolha;
import br.com.campusconnect.bolha.model.MembroBolhaId;
import br.com.campusconnect.bolha.model.TipoBolha;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembroBolhaRepository extends JpaRepository<MembroBolha, MembroBolhaId> {

    /** Bolhas em que o usuário entrou, cada uma com o total de membros. */
    @Query("""
            select b.id as id, b.nome as nome, b.slug as slug, b.tipo as tipo,
                   (select count(outro) from MembroBolha outro where outro.bolha = b) as totalMembros
            from MembroBolha m join m.bolha b
            where m.id.usuarioId = :usuarioId
            """)
    List<BolhaComTotal> listarBolhasDoUsuario(@Param("usuarioId") UUID usuarioId);

    interface BolhaComTotal {

        UUID getId();

        String getNome();

        String getSlug();

        TipoBolha getTipo();

        long getTotalMembros();
    }
}
