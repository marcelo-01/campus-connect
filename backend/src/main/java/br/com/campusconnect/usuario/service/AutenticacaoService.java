package br.com.campusconnect.usuario.service;

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
 * Cadastra, verifica o e-mail e abre, renova e encerra sessões. Cada sessão é um JWT de acesso curto mais um refresh
 * token longo. A conta só tem sessão depois de verificar o e-mail.
 */
@Service
@RequiredArgsConstructor
public class AutenticacaoService {

    private final AuthenticationManager autenticador;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarios;
    private final TokenAcessoService tokensAcesso;
    private final RefreshTokenService refreshTokens;
    private final VerificacaoEmailService verificacoes;

    /**
     * Cria a conta e manda o código para o e-mail. A sessão só abre em {@link #verificarEmail}.
     *
     * <p>Se o e-mail for de uma conta que nunca foi verificada, ela é refeita com os dados novos. Nesse caso, se a
     * conta acabou de receber um código, não vai outro: o que foi mandado continua valendo.
     */
    @Transactional
    public CadastroPendente cadastrar(NovoUsuario novo) {
        Usuario usuario = usuarioService.cadastrar(novo);
        verificacoes.emitirSePuder(usuario);
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
        Usuario usuario = verificacoes.confirmar(email, codigo);
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
                .ifPresent(verificacoes::emitir);
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
            usuarios.findByEmail(Usuario.normalizarEmail(email)).ifPresent(verificacoes::emitirSePuder);
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
