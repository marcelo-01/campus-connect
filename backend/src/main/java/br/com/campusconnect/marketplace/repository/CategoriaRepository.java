package br.com.campusconnect.marketplace.repository;

import br.com.campusconnect.marketplace.model.CategoriaAnuncio;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaRepository extends JpaRepository<CategoriaAnuncio, UUID> {

    /** As categorias ativas: primeiro as de serviço, depois as de produto, com "Outros" no fim de cada tipo. */
    @Query(value = """
            select * from categoria_anuncio
            where ativa
            order by case tipo when 'SERVICO' then 0 else 1 end, slug like 'outros-%', nome
            """, nativeQuery = true)
    List<CategoriaAnuncio> ativasEmOrdem();
}
