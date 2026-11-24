package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.model.FinalidadeCodigo;
import br.com.campusconnect.usuario.model.RefreshToken;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import br.com.campusconnect.usuario.seguranca.ContaUsuario;
import br.com.campusconnect.usuario.seguranca.RefreshTokenService;
import br.com.campusconnect.usuario.seguranca.RefreshTokenService.Rotacao;
import br.com.campusconnect.usuario.seguranca.RefreshTokenService.TokenRenovacao;
import br.com.campusconnect.usuario.seguranca.TokenAcessoService;
import br.com.campusconnect.usuario.seguranca.TokenAcessoService.TokenAcesso;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastra, verifica o e-mail, troca a senha e abre, renova e encerra sessões. Cada sessão é um JWT de acesso curto
 * mais um refresh token longo. A conta só tem sessão depois de verificar o e-mail.
 */
@Service
@RequiredArgsConstructor
public class AutenticacaoService {

    static final String SENHA_ATUAL_INCORRETA = "A senha atual está incorreta.";
    static final String SENHA_REPETIDA = "A senha nova precisa ser diferente da atual.";

    private final AuthenticationManager autenticador;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarios;
    private final TokenAcessoService tokensAcesso;
    private final RefreshTokenService refreshTokens;
    private final VerificacaoEmailService verificacoes;
    private final SenhaService senhas;

    /**
     * Cria a conta e manda o código para o e-mail. A sessão só abre em {@link #verificarEmail}.
     *
     * <p>Se o e-mail for de uma conta que nunca foi verificada, ela é refeita com os dados novos. Nesse caso, se a
     * conta acabou de receber um código, não vai outro: o que foi mandado continua valendo.
     */
    @Transactional
    public CadastroPendente cadastrar(NovoUsuario novo) {
        Usuario usuario = usuarioService.cadastrar(novo);
        verificacoes.emitirSePuder(usuario, FinalidadeCodigo.VERIFICACAO_EMAIL);
        return new CadastroPendente(usuario.getEmail());
    }

    /**
     * Confere o código mandado para o e-mail e já entra na conta, com "Manter conectado" ligado, como era no cadastro.
     *
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws EmailJaVerificadoException se a conta já tinha verificado o e-mail
     * @throws DisabledException se a conta foi suspensa ou desativada antes de verificar
     */
    @Transactional(noRollbackFor = CodigoIncorretoException.class)
    public SessaoEmitida verificarEmail(String email, String codigo) {
        Usuario usuario = verificacoes.confirmarEmail(email, codigo);
        if (!usuario.podeEntrar()) {
            throw new DisabledException("Conta fora do ar");
        }
        return abrirSessao(usuario, true);
    }

    /**
     * Manda outro código para a conta que ainda não verificou o e-mail. Para um e-mail sem conta, ou de conta já
     * verificada ou fora do ar, não faz nada e não diz nada.
     *
     * @throws LimiteDeCodigosException se a conta pediu códigos demais em pouco tempo
     */
    @Transactional
    public void reenviarCodigo(String email) {
        usuarios.findByEmail(Usuario.normalizarEmail(email))
                .filter(usuario -> !usuario.isEmailVerificado() && usuario.estaAtiva())
                .ifPresent(usuario -> verificacoes.emitir(usuario, FinalidadeCodigo.VERIFICACAO_EMAIL));
    }

    /**
     * O "Esqueceu a senha?": manda para o e-mail o código que troca a senha. Para um e-mail sem conta, ou de conta
     * fora do ar, não faz nada e não diz nada. A conta que ainda não verificou o e-mail também recebe o código.
     *
     * @throws LimiteDeCodigosException se a conta pediu códigos demais em pouco tempo
     */
    @Transactional
    public void esqueciSenha(String email) {
        usuarios.findByEmail(Usuario.normalizarEmail(email))
                .filter(Usuario::estaAtiva)
                .ifPresent(usuario -> verificacoes.emitir(usuario, FinalidadeCodigo.REDEFINICAO_SENHA));
    }

    /**
     * Confere o código de redefinição antes de a pessoa digitar a senha nova. O código não é gasto: ele vai de novo
     * em {@link #redefinirSenha}.
     *
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws CampoInvalidoException se não houver código valendo para o e-mail
     */
    @Transactional(noRollbackFor = CodigoIncorretoException.class)
    public void conferirCodigoRedefinicao(String email, String codigo) {
        verificacoes.conferirRedefinicao(email, codigo);
    }

    /**
     * Troca a senha pelo código mandado para o e-mail e já entra na conta, com "Manter conectado" ligado, como na
     * verificação do e-mail. As sessões abertas em outros aparelhos caem.
     *
     * <p>Quem recebeu o código provou ser dono do e-mail, então a conta que ainda não tinha verificado o e-mail fica
     * verificada.
     *
     * @throws CodigoIncorretoException se o código não bater; a tentativa fica contada
     * @throws CampoInvalidoException se não houver código valendo para o e-mail
     */
    @Transactional(noRollbackFor = CodigoIncorretoException.class)
    public SessaoEmitida redefinirSenha(String email, String codigo, String novaSenha) {
        Usuario usuario = verificacoes.conferirRedefinicao(email, codigo);
        usuario.setEmailVerificado(true);
        verificacoes.apagarTodos(usuario);
        senhas.trocar(usuario, novaSenha);
        return abrirSessao(usuario, true);
    }

    /**
     * A troca de senha de quem está logado, que se reconhece pelo refresh token do cookie e confirma a senha atual.
     * As sessões da conta caem em todos os aparelhos, e a deste recomeça numa família nova, com o mesmo "Manter
     * conectado".
     *
     * <p>Não desfaz a transação na {@link SessaoInvalidaException}, como em {@link #renovar}.
     *
     * @throws SessaoInvalidaException se o token não valer mais ou a conta não puder entrar
     * @throws CampoInvalidoException se a senha atual estiver errada ou a nova for igual a ela
     */
    @Transactional(noRollbackFor = SessaoInvalidaException.class)
    public SessaoEmitida alterarSenha(String refreshToken, String senhaAtual, String novaSenha) {
        RefreshToken sessao = refreshTokens.sessaoAtual(refreshToken);
        Usuario usuario = sessao.getUsuario();
        if (!usuario.podeEntrar()) {
            refreshTokens.revogarFamilia(refreshToken);
            throw new SessaoInvalidaException();
        }
        if (!senhas.confere(usuario, senhaAtual)) {
            throw new CampoInvalidoException("senhaAtual", SENHA_ATUAL_INCORRETA);
        }
        if (senhas.confere(usuario, novaSenha)) {
            throw new CampoInvalidoException("novaSenha", SENHA_REPETIDA);
        }
        senhas.trocar(usuario, novaSenha);
        return abrirSessao(usuario, sessao.isPersistente());
    }

    /**
     * Com a senha certa e o e-mail ainda por verificar, manda um código novo, se o limite deixar, antes de recusar.
     * Esse código precisa ficar gravado, então a {@link EmailNaoVerificadoException} não desfaz a transação.
     *
     * @throws BadCredentialsException se o e-mail não existir ou a senha estiver errada
     * @throws EmailNaoVerificadoException se a senha estiver certa, mas o e-mail não tiver sido verificado
     * @throws AccountStatusException se a senha estiver certa, mas a conta não estiver ativa
     */
    @Transactional(noRollbackFor = EmailNaoVerificadoException.class)
    public SessaoEmitida entrar(String email, String senha, boolean manterConectado) {
        Authentication autenticacao;
        try {
            autenticacao = autenticador.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, senha));
        } catch (EmailNaoVerificadoException naoVerificado) {
            usuarios.findByEmail(Usuario.normalizarEmail(email))
                    .ifPresent(usuario -> verificacoes.emitirSePuder(usuario, FinalidadeCodigo.VERIFICACAO_EMAIL));
            throw naoVerificado;
        }
        ContaUsuario conta = (ContaUsuario) autenticacao.getPrincipal();
        return abrirSessao(usuarios.getReferenceById(conta.getId()), manterConectado);
    }

    /**
     * Troca o refresh token por um novo e emite outro JWT de acesso.
     *
     * <p>Não desfaz a transação na {@link SessaoInvalidaException}: a revogação da família, quando há
     * reuso de token ou a conta deixou de estar ativa, precisa ficar gravada.
     *
     * @throws SessaoInvalidaException se o token não valer mais ou a conta não puder entrar
     */
    @Transactional(noRollbackFor = SessaoInvalidaException.class)
    public SessaoEmitida renovar(String refreshToken) {
        Rotacao rotacao = refreshTokens.rotacionar(refreshToken);
        Usuario usuario = rotacao.usuario();
        if (!usuario.podeEntrar()) {
            refreshTokens.revogarFamilia(rotacao.token().valor());
            throw new SessaoInvalidaException();
        }
        usuario.setUltimoAcessoEm(Instant.now());
        return emitir(usuario, rotacao.token());
    }

    /** Revoga a família do refresh token. Token desconhecido ou já revogado não é erro. */
    @Transactional
    public void sair(String refreshToken) {
        refreshTokens.revogarFamilia(refreshToken);
    }

    private SessaoEmitida abrirSessao(Usuario usuario, boolean persistente) {
        usuario.setUltimoAcessoEm(Instant.now());
        return emitir(usuario, refreshTokens.criar(usuario, persistente));
    }

    private SessaoEmitida emitir(Usuario usuario, TokenRenovacao refresh) {
        TokenAcesso acesso = tokensAcesso.emitir(usuario.getId());
        return new SessaoEmitida(acesso.valor(), acesso.expiraEm(), refresh.valor(), refresh.expiraEm(),
                refresh.persistente(), UsuarioResumo.de(usuario));
    }
}
