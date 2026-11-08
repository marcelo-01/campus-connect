package br.com.campusconnect.bolha.service;

import br.com.campusconnect.bolha.repository.MembroBolhaRepository;
import br.com.campusconnect.bolha.repository.MembroBolhaRepository.BolhaComTotal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BolhaService {

    private final MembroBolhaRepository membros;

    /** Na ordem da hierarquia (instituição, campus, curso, comunidade) e, dentro de cada nível, por nome. */
    @Transactional(readOnly = true)
    public List<BolhaResumo> listarDoUsuario(UUID usuarioId) {
        return membros.listarBolhasDoUsuario(usuarioId).stream()
                .sorted(Comparator.comparing((BolhaComTotal b) -> b.getTipo().ordinal())
                        .thenComparing(BolhaComTotal::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(b -> new BolhaResumo(b.getId(), b.getNome(), b.getSlug(), b.getTipo().name(),
                        b.getTotalMembros()))
                .toList();
    }
}
