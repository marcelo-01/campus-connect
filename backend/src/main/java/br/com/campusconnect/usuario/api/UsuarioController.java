package br.com.campusconnect.usuario.api;

import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.usuario.service.Perfil;
import br.com.campusconnect.usuario.service.PerfilService;
import br.com.campusconnect.usuario.service.UsuarioResumo;
import br.com.campusconnect.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** O {@code sub} do JWT é o id do usuário logado. */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
class UsuarioController {

    private final UsuarioService usuarios;
    private final PerfilService perfis;

    @GetMapping("/me")
    ResponseEntity<UsuarioResumo> eu(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(usuarios.buscarResumo(idDe(jwt)));
    }

    @GetMapping("/{username}/perfil")
    ResponseEntity<Perfil> perfil(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(perfis.buscar(username, idDe(jwt)));
    }

    @PutMapping("/me/perfil")
    Perfil atualizarPerfil(@Valid @RequestBody AlteracaoPerfilRequest requisicao, @AuthenticationPrincipal Jwt jwt) {
        return perfis.atualizar(idDe(jwt), requisicao.paraAlteracao());
    }

    @PostMapping(path = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Perfil trocarFoto(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal Jwt jwt)
            throws IOException {
        ImagemService.garantirTamanho(arquivo.getSize());
        return perfis.trocarFoto(idDe(jwt), arquivo.getBytes());
    }

    @PostMapping(path = "/me/capa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Perfil trocarCapa(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal Jwt jwt)
            throws IOException {
        ImagemService.garantirTamanho(arquivo.getSize());
        return perfis.trocarCapa(idDe(jwt), arquivo.getBytes());
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
