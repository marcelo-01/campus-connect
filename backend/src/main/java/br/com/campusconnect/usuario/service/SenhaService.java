package br.com.campusconnect.usuario.service;

import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.seguranca.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confere e troca a senha da conta. Exige uma transação aberta por quem chama ({@link AutenticacaoService}), que
 * abre a sessão nova no mesmo commit.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class SenhaService {

    private final PasswordEncoder codificador;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher eventos;

    boolean confere(Usuario usuario, String senha) {
        return codificador.matches(senha, usuario.getSenhaHash());
    }

    /**
     * Grava a senha nova e encerra as sessões da conta em todos os aparelhos. Quem tinha a senha antiga, ou um cookie
     * copiado, perde o acesso: o refresh token deixa de valer na hora, e o JWT de acesso vence sozinho em poucos
     * minutos ({@code duracao-acesso}). Depois do commit, o dono da conta recebe um aviso por e-mail
     * ({@link SenhaAlterada}).
     */
    void trocar(Usuario usuario, String novaSenha) {
        usuario.setSenhaHash(codificador.encode(novaSenha));
        refreshTokens.revogarDaConta(usuario);
        eventos.publishEvent(new SenhaAlterada(usuario.getEmail(), usuario.getNomeCompleto()));
    }
}
