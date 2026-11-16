package br.com.campusconnect.marketplace.repository;

import br.com.campusconnect.marketplace.model.AvaliacaoAnuncio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvaliacaoRepository extends JpaRepository<AvaliacaoAnuncio, UUID> {

    /** As avaliações de um anúncio, das mais novas para as mais antigas. */
    List<AvaliacaoAnuncio> findTop100ByAnuncio_IdOrderByCriadoEmDescIdDesc(UUID anuncioId);

    Optional<AvaliacaoAnuncio> findByAnuncio_IdAndAutorId(UUID anuncioId, UUID autorId);

    Optional<AvaliacaoAnuncio> findByIdAndAnuncio_Id(UUID id, UUID anuncioId);
}
