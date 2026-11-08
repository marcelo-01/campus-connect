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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Abre, renova e encerra sessões. Cada sessão é um JWT de acesso curto mais um refresh token longo. */
@Service
@RequiredArgsConstructor
public class AutenticacaoService {

    private final AuthenticationManager autenticador;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarios;
    private final TokenAcessoService tokensAcesso;
    private final RefreshTokenService refreshTokens;

    /** Cria a conta e já entra nela, com "Manter conectado" ligado. */
    @Transactional
    public SessaoEmitida cadastrar(NovoUsuario novo) {
        UsuarioResumo criado = usuarioService.cadastrar(novo);
        Usuario usuario = usuarios.getReferenceById(criado.id());
        return abrirSessao(usuario, true);
    }

    /**
     * @throws BadCredentialsException se o e-mail não existir ou a senha estiver errada
     * @throws AccountStatusException se a senha estiver certa, mas a conta não estiver ativa
     */
    @Transactional
    public SessaoEmitida entrar(String email, String senha, boolean manterConectado) {
        Authentication autenticacao = autenticador.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, senha));
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
