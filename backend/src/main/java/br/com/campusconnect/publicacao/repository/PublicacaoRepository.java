package br.com.campusconnect.publicacao.repository;

import br.com.campusconnect.publicacao.model.Publicacao;
import br.com.campusconnect.publicacao.model.StatusPublicacao;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacaoRepository extends JpaRepository<Publicacao, UUID> {

    Optional<Publicacao> findByIdAndStatus(UUID id, StatusPublicacao status);

    /** As publicações de uma página do feed, já com as mídias, numa consulta só. */
    @Query("select distinct p from Publicacao p left join fetch p.midias where p.id in :ids")
    List<Publicacao> carregarComMidias(@Param("ids") Collection<UUID> ids);
}
