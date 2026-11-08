package br.com.campusconnect.usuario.seguranca;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite o JWT de acesso. O {@code sub} é o id do usuário; os outros módulos o leem do {@code Jwt} autenticado. */
@Service
@RequiredArgsConstructor
public class TokenAcessoService {

    static final String EMISSOR = "campusconnect";

    private final JwtEncoder codificador;
    private final PropriedadesSeguranca propriedades;

    public TokenAcesso emitir(UUID usuarioId) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(propriedades.duracaoAcesso());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .subject(usuarioId.toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenAcesso(valor, expiraEm);
    }

    public record TokenAcesso(String valor, Instant expiraEm) {
    }
}
