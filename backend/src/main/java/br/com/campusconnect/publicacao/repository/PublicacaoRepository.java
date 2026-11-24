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

    List<Publicacao> findByIdInAndStatus(Collection<UUID> ids, StatusPublicacao status);

    /**
     * As publicações de uma página do feed, já com as mídias, numa consulta só. As bolhas vêm à parte, em
     * {@link #bolhasDe}: buscá-las aqui junto repetiria cada mídia uma vez por bolha.
     */
    @Query("select distinct p from Publicacao p left join fetch p.midias where p.id in :ids")
    List<Publicacao> carregarComMidias(@Param("ids") Collection<UUID> ids);

    /** As bolhas de cada publicação, em lote: uma linha por publicação e bolha. */
    @Query("select p.id as publicacaoId, b as bolhaId from Publicacao p join p.bolhaIds b where p.id in :ids")
    List<BolhaDaPublicacao> bolhasDe(@Param("ids") Collection<UUID> ids);

    interface BolhaDaPublicacao {

        UUID getPublicacaoId();

        UUID getBolhaId();
    }
}
