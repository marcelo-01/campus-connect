package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.UsuarioResumo;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
class UsuarioController {

    private final UsuarioService usuarios;

    /** Conta dona do JWT de acesso. O {@code sub} do token é o id do usuário. */
    @GetMapping("/me")
    ResponseEntity<UsuarioResumo> eu(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(usuarios.buscarResumo(UUID.fromString(jwt.getSubject())));
    }
}
