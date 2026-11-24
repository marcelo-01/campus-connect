package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.model.VerificacaoEmail;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import br.com.campusconnect.usuario.repository.VerificacaoEmailRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manda e confere o código de 6 dígitos que verifica o e-mail.
 *
 * <p>Só o código mais novo da conta vale. Ele vale por {@code validade-codigo} e aceita {@code tentativas-por-codigo}
 * erros. A mesma conta recebe no máximo um código a cada {@code intervalo-reenvio} e {@code envios-por-hora} por
 * hora. Assim, adivinhar o código em 1 milhão fica inviável.
 *
 * <p>Exige uma transação aberta por quem chama ({@link AutenticacaoService}). Com o código errado, a tentativa
 * contada precisa ficar gravada, então a {@link CodigoIncorretoException} não desfaz a transação.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = CodigoIncorretoException.class)
class VerificacaoEmailService {

    /** O período em que se contam os {@code envios-por-hora}. */
    static final Duration JANELA_ENVIOS = Duration.ofHours(1);

    static final String CODIGO_VENCIDO = "Este código não vale mais. Peça um código novo.";

    private final VerificacaoEmailRepository verificacoes;
    private final UsuarioRepository usuarios;
    private final GeradorCodigoVerificacao gerador;
    private final PropriedadesVerificacao propriedades;
    private final ApplicationEventPublisher eventos;

    /**
     * Manda um código novo. O anterior deixa de valer.
     *
     * @throws LimiteDeCodigosException se a conta pediu códigos demais em pouco tempo
     */
    void emitir(Usuario usuario) {
        Instant agora = Instant.now();
        Duration espera = espera(usuario, agora);
        if (espera.isPositive()) {
            throw new LimiteDeCodigosException(espera);
        }
        mandar(usuario, agora);
    }

    /**
     * Manda um código novo se o limite deixar. Se não deixar, nada muda, e o último código mandado continua valendo
     * enquanto estiver no prazo. Serve ao cadastro refeito e ao login, que não falham por causa do limite.
     */
    void emitirSePuder(Usuario usuario) {
        Instant agora = Instant.now();
        if (!espera(usuario, agora).isPositive()) {
            mandar(usuario, agora);
        }
    }

    /**
     * Confere o código e, se ele bater, marca o e-mail como verificado e apaga os códigos da conta.
     *
     * @return a conta, com o e-mail verificado
     * @throws EmailJaVerificadoException se a conta já tinha verificado o e-mail
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws CampoInvalidoException se a conta não existir ou não houver código valendo
     */
    Usuario confirmar(String email, String codigo) {
        Instant agora = Instant.now();
        Usuario usuario = usuarios.findByEmail(Usuario.normalizarEmail(email))
                .orElseThrow(VerificacaoEmailService::vencido);
        if (usuario.isEmailVerificado()) {
            throw new EmailJaVerificadoException();
        }
        VerificacaoEmail atual = verificacoes.findFirstByUsuarioIdOrderByCriadoEmDesc(usuario.getId())
                .filter(verificacao -> verificacao.valeEm(agora, propriedades.tentativasPorCodigo()))
                .orElseThrow(VerificacaoEmailService::vencido);
        if (!confere(atual.getCodigoHash(), codigo)) {
            throw new CodigoIncorretoException(atual.registrarErro(propriedades.tentativasPorCodigo()));
        }
        usuario.setEmailVerificado(true);
        verificacoes.apagarDaConta(usuario.getId());
        return usuario;
    }

    /** Zero se a conta pode receber outro código agora. */
    private Duration espera(Usuario usuario, Instant agora) {
        List<VerificacaoEmail> recentes = verificacoes.findByUsuarioIdAndCriadoEmAfterOrderByCriadoEmDesc(
                usuario.getId(), agora.minus(JANELA_ENVIOS));
        Duration espera = Duration.ZERO;
        if (!recentes.isEmpty()) {
            Instant liberaEm = recentes.getFirst().getCriadoEm().plus(propriedades.intervaloReenvio());
            espera = maior(espera, Duration.between(agora, liberaEm));
        }
        if (recentes.size() >= propriedades.enviosPorHora()) {
            // O mais antigo dos últimos N sai da janela primeiro.
            Instant liberaEm = recentes.get(propriedades.enviosPorHora() - 1).getCriadoEm().plus(JANELA_ENVIOS);
            espera = maior(espera, Duration.between(agora, liberaEm));
        }
        return espera;
    }

    private void mandar(Usuario usuario, Instant agora) {
        String codigo = gerador.gerar();
        verificacoes.save(new VerificacaoEmail(usuario, hash(codigo), agora.plus(propriedades.validadeCodigo())));
        eventos.publishEvent(new CodigoVerificacaoEmitido(usuario.getEmail(), usuario.getNomeCompleto(), codigo,
                propriedades.validadeCodigo()));
    }

    private static Duration maior(Duration uma, Duration outra) {
        return uma.compareTo(outra) >= 0 ? uma : outra;
    }

    private static CampoInvalidoException vencido() {
        return new CampoInvalidoException("codigo", CODIGO_VENCIDO);
    }

    /** Em tempo constante, para o tempo de resposta não dizer quantos caracteres bateram. */
    private static boolean confere(String hashGravado, String codigo) {
        return MessageDigest.isEqual(hashGravado.getBytes(StandardCharsets.US_ASCII),
                hash(codigo).getBytes(StandardCharsets.US_ASCII));
    }

    static String hash(String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException("Toda JVM tem SHA-256", impossivel);
        }
    }
}
