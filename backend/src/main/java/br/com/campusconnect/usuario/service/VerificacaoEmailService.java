package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.model.FinalidadeCodigo;
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
 * Manda e confere os códigos de 6 dígitos mandados para o e-mail: o que verifica o e-mail e o que redefine a senha
 * ({@link FinalidadeCodigo}).
 *
 * <p>Em cada finalidade, só o código mais novo da conta vale. Ele vale por {@code validade-codigo} e aceita
 * {@code tentativas-por-codigo} erros. A mesma conta recebe no máximo um código a cada {@code intervalo-reenvio} e
 * {@code envios-por-hora} por hora, contados em cada finalidade. Assim, adivinhar o código em 1 milhão fica inviável.
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
     * Manda um código novo. O anterior da mesma finalidade deixa de valer.
     *
     * @throws LimiteDeCodigosException se a conta pediu códigos demais dessa finalidade em pouco tempo
     */
    void emitir(Usuario usuario, FinalidadeCodigo finalidade) {
        Instant agora = Instant.now();
        Duration espera = espera(usuario, finalidade, agora);
        if (espera.isPositive()) {
            throw new LimiteDeCodigosException(espera);
        }
        mandar(usuario, finalidade, agora);
    }

    /**
     * Manda um código novo se o limite deixar. Se não deixar, nada muda, e o último código mandado continua valendo
     * enquanto estiver no prazo. Serve ao cadastro refeito e ao login, que não falham por causa do limite.
     */
    void emitirSePuder(Usuario usuario, FinalidadeCodigo finalidade) {
        Instant agora = Instant.now();
        if (!espera(usuario, finalidade, agora).isPositive()) {
            mandar(usuario, finalidade, agora);
        }
    }

    /**
     * Confere o código de verificação e, se ele bater, marca o e-mail como verificado e apaga os códigos de
     * verificação da conta.
     *
     * @return a conta, com o e-mail verificado
     * @throws EmailJaVerificadoException se a conta já tinha verificado o e-mail
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws CampoInvalidoException se a conta não existir ou não houver código valendo
     */
    Usuario confirmarEmail(String email, String codigo) {
        Usuario usuario = usuarios.findByEmail(Usuario.normalizarEmail(email))
                .orElseThrow(VerificacaoEmailService::vencido);
        if (usuario.isEmailVerificado()) {
            throw new EmailJaVerificadoException();
        }
        conferir(usuario, FinalidadeCodigo.VERIFICACAO_EMAIL, codigo);
        usuario.setEmailVerificado(true);
        verificacoes.apagarDaConta(usuario.getId(), FinalidadeCodigo.VERIFICACAO_EMAIL);
        return usuario;
    }

    /**
     * Confere o código de redefinição de senha sem gastá-lo: ele continua valendo para o envio da senha nova. Quem
     * gasta o código é {@link #apagarTodos}.
     *
     * @return a conta do e-mail, ativa
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws CampoInvalidoException se a conta não existir, não estiver ativa ou não houver código valendo
     */
    Usuario conferirRedefinicao(String email, String codigo) {
        Usuario usuario = usuarios.findByEmail(Usuario.normalizarEmail(email))
                .filter(Usuario::estaAtiva)
                .orElseThrow(VerificacaoEmailService::vencido);
        conferir(usuario, FinalidadeCodigo.REDEFINICAO_SENHA, codigo);
        return usuario;
    }

    /** Apaga os códigos da conta, das duas finalidades. */
    void apagarTodos(Usuario usuario) {
        verificacoes.apagarTodosDaConta(usuario.getId());
    }

    private void conferir(Usuario usuario, FinalidadeCodigo finalidade, String codigo) {
        VerificacaoEmail atual = verificacoes
                .findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), finalidade)
                .filter(verificacao -> verificacao.valeEm(Instant.now(), propriedades.tentativasPorCodigo()))
                .orElseThrow(VerificacaoEmailService::vencido);
        if (!confere(atual.getCodigoHash(), codigo)) {
            throw new CodigoIncorretoException(atual.registrarErro(propriedades.tentativasPorCodigo()));
        }
    }

    /** Zero se a conta pode receber outro código dessa finalidade agora. */
    private Duration espera(Usuario usuario, FinalidadeCodigo finalidade, Instant agora) {
        List<VerificacaoEmail> recentes = verificacoes.findByUsuarioIdAndFinalidadeAndCriadoEmAfterOrderByCriadoEmDesc(
                usuario.getId(), finalidade, agora.minus(JANELA_ENVIOS));
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

    private void mandar(Usuario usuario, FinalidadeCodigo finalidade, Instant agora) {
        String codigo = gerador.gerar();
        Duration validade = propriedades.validadeCodigo();
        verificacoes.save(new VerificacaoEmail(usuario, finalidade, hash(codigo), agora.plus(validade)));
        eventos.publishEvent(switch (finalidade) {
            case VERIFICACAO_EMAIL ->
                    new CodigoVerificacaoEmitido(usuario.getEmail(), usuario.getNomeCompleto(), codigo, validade);
            case REDEFINICAO_SENHA ->
                    new CodigoRedefinicaoSenhaEmitido(usuario.getEmail(), usuario.getNomeCompleto(), codigo, validade);
        });
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
