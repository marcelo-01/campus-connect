package br.com.campusconnect.bolha.repository;

import br.com.campusconnect.bolha.model.Bolha;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BolhaRepository extends JpaRepository<Bolha, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByBolhaPaiId(UUID bolhaPaiId);

    /** A própria bolha e todas as que ficam abaixo dela, em qualquer nível. */
    @Query(value = """
            with recursive subarvore(id) as (
                select id from bolha where id = :id
                union all
                select filha.id from bolha filha join subarvore on filha.bolha_pai_id = subarvore.id
            )
            select id from subarvore
            """, nativeQuery = true)
    List<UUID> idsDaSubarvore(@Param("id") UUID id);
}
