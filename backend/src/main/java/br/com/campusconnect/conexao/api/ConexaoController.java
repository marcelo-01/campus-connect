package br.com.campusconnect.conexao.api;

import br.com.campusconnect.conexao.service.ConexaoService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conexoes")
@RequiredArgsConstructor
class ConexaoController {

    private final ConexaoService conexoes;

    @GetMapping("/usuario/{usuarioId}/total")
    Total total(@PathVariable UUID usuarioId) {
        return new Total(conexoes.contarConexoes(usuarioId));
    }

    record Total(long total) {
    }
}
