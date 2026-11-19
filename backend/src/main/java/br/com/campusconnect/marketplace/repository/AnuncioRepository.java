package br.com.campusconnect.marketplace.repository;

import br.com.campusconnect.marketplace.model.Anuncio;
import br.com.campusconnect.marketplace.model.StatusAnuncio;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioRepository extends JpaRepository<Anuncio, UUID> {

    /** Os anúncios de uma página da vitrine, já com a categoria e as fotos, numa consulta só. */
    @Query("select distinct a from Anuncio a join fetch a.categoria left join fetch a.imagens where a.id in :ids")
    List<Anuncio> carregarComImagens(@Param("ids") Collection<UUID> ids);

    List<Anuncio> findByIdInAndStatusIn(Collection<UUID> ids, Collection<StatusAnuncio> status);
}
