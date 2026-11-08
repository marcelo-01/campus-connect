package br.com.campusconnect.usuario.seguranca;

import br.com.campusconnect.usuario.model.RefreshToken;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.RefreshTokenRepository;
import br.com.campusconnect.usuario.service.SessaoInvalidaException;
import br.com.campusconnect.usuario.service.SessaoJaRenovadaException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria, troca e revoga refresh tokens.
 *
 * <p>Exige uma transação aberta por quem chama ({@code AutenticacaoService}), que confere a conta e
 * atualiza o último acesso no mesmo commit.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = SessaoInvalidaException.class)
public class RefreshTokenService {

    /**
     * Um token trocado há menos tempo que isso e usado de novo é tratado como corrida entre abas do
     * mesmo navegador, não como roubo: a família continua valendo e o cliente tenta de novo.
     */
    static final Duration TOLERANCIA_REUSO = Duration.ofSeconds(30);

    private static final int BYTES_TOKEN = 32;

    private final RefreshTokenRepository tokens;
    private final PropriedadesSeguranca propriedades;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Abre uma família nova, usada no login e no cadastro. */
    public TokenRenovacao criar(Usuario usuario, boolean persistente) {
        return salvar(usuario, UUID.randomUUID(), persistente, Instant.now());
    }

    /**
     * Revoga o token recebido e devolve outro da mesma família, com a validade recomeçando.
     *
     * @throws SessaoJaRenovadaException se o token foi trocado há menos de {@link #TOLERANCIA_REUSO}
     * @throws SessaoInvalidaException se o token não existir, tiver vencido ou tiver sido revogado antes
     */
    public Rotacao rotacionar(String valor) {
        Instant agora = Instant.now();
        RefreshToken atual = tokens.findByTokenHash(hash(valor)).orElseThrow(SessaoInvalidaException::new);

        if (atual.isRevogado()) {
            boolean trocadoAgoraHaPouco = !atual.getRevogadoEm().isBefore(agora.minus(TOLERANCIA_REUSO));
            boolean familiaAtiva = tokens.existsByFamiliaIdAndRevogadoEmIsNullAndExpiraEmAfter(
                    atual.getFamiliaId(), agora);
            if (trocadoAgoraHaPouco && familiaAtiva) {
                throw new SessaoJaRenovadaException();
            }
            // Reuso de um token antigo: alguém pode ter copiado o cookie. Derruba a família inteira.
            // Depois de um logout a família já está toda revogada, e isto não muda nada.
            tokens.revogarFamilia(atual.getFamiliaId(), agora);
            throw new SessaoInvalidaException();
        }
        if (atual.expirouEm(agora)) {
            throw new SessaoInvalidaException();
        }

        atual.revogar(agora);
        TokenRenovacao novo = salvar(atual.getUsuario(), atual.getFamiliaId(), atual.isPersistente(), agora);
        return new Rotacao(atual.getUsuario(), novo);
    }

    /** Usado no logout e quando a conta deixa de estar ativa. Token desconhecido é ignorado. */
    public void revogarFamilia(String valor) {
        tokens.findByTokenHash(hash(valor))
                .ifPresent(token -> tokens.revogarFamilia(token.getFamiliaId(), Instant.now()));
    }

    private TokenRenovacao salvar(Usuario usuario, UUID familiaId, boolean persistente, Instant agora) {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiraEm = agora.plus(propriedades.duracaoRefresh(persistente));
        tokens.save(new RefreshToken(usuario, familiaId, hash(valor), persistente, expiraEm));
        return new TokenRenovacao(valor, expiraEm, persistente);
    }

    static String hash(String valor) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException("Toda JVM tem SHA-256", impossivel);
        }
    }

    public record TokenRenovacao(String valor, Instant expiraEm, boolean persistente) {

        @Override
        public String toString() {
            return "TokenRenovacao[expiraEm=" + expiraEm + ", persistente=" + persistente + "]";
        }
    }

    public record Rotacao(Usuario usuario, TokenRenovacao token) {
    }
}
