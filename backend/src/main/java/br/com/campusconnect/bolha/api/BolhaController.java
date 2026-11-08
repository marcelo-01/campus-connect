package br.com.campusconnect.bolha.api;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.bolha.service.BolhaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bolhas")
@RequiredArgsConstructor
class BolhaController {

    private final BolhaService bolhas;

    /** Bolhas de um usuário: aparecem no perfil e no menu lateral. */
    @GetMapping("/usuario/{usuarioId}")
    List<BolhaResumo> doUsuario(@PathVariable UUID usuarioId) {
        return bolhas.listarDoUsuario(usuarioId);
    }
}
