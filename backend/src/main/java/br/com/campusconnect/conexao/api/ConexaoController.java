package br.com.campusconnect.conexao.api;

import br.com.campusconnect.conexao.service.ConexaoService;
import br.com.campusconnect.conexao.service.MinhasConexoes;
import br.com.campusconnect.conexao.service.PessoaNaConexao;
import br.com.campusconnect.conexao.service.SituacaoConexao;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conexões entre usuários. As ações em {@code /com/{usuarioId}} recebem o id da outra pessoa e respondem como a
 * relação ficou para quem agiu ({@link SituacaoConexao}), para a tela se acertar mesmo se estava desatualizada.
 */
@RestController
@RequestMapping("/api/conexoes")
@RequiredArgsConstructor
class ConexaoController {

    private final ConexaoService conexoes;

    /** Conexões, pedidos recebidos e pedidos enviados de quem está logado. */
    @GetMapping("/minhas")
    MinhasConexoes minhas(@AuthenticationPrincipal Jwt jwt) {
        return conexoes.minhas(idDe(jwt));
    }

    @GetMapping("/usuario/{usuarioId}")
    List<PessoaNaConexao> doUsuario(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return conexoes.listarConexoes(usuarioId, idDe(jwt));
    }

    @GetMapping("/usuario/{usuarioId}/total")
    Total total(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return new Total(conexoes.contarConexoes(usuarioId, idDe(jwt)));
    }

    /** Pede a conexão, ou aceita, se a outra pessoa já tinha pedido. */
    @PostMapping("/com/{usuarioId}")
    Situacao conectar(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return new Situacao(conexoes.conectar(idDe(jwt), usuarioId));
    }

    @PostMapping("/com/{usuarioId}/recusa")
    Situacao recusar(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return new Situacao(conexoes.recusar(idDe(jwt), usuarioId));
    }

    @DeleteMapping("/com/{usuarioId}/pedido")
    Situacao cancelarPedido(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return new Situacao(conexoes.cancelarPedido(idDe(jwt), usuarioId));
    }

    @DeleteMapping("/com/{usuarioId}")
    Situacao desfazer(@PathVariable UUID usuarioId, @AuthenticationPrincipal Jwt jwt) {
        return new Situacao(conexoes.desfazer(idDe(jwt), usuarioId));
    }

    private static UUID idDe(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record Total(long total) {
    }

    record Situacao(SituacaoConexao situacao) {
    }
}
