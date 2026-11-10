package br.com.campusconnect.usuario.service;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemInvalidaException;
import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.usuario.model.Usuario;
import br.com.campusconnect.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final RegrasConta regras;
    private final ImagemService imagens;

    /**
     * Perfil pelo username. Conta suspensa, banida, desativada ou anonimizada não aparece para os outros,
     * só para a própria pessoa.
     */
    @Transactional(readOnly = true)
    public Optional<Perfil> buscar(String username, UUID quemVe) {
        return usuarios.findByUsername(Usuario.normalizarUsername(username))
                .filter(usuario -> usuario.getId().equals(quemVe) || usuario.podeEntrar())
                .map(usuario -> Perfil.de(usuario, usuario.getId().equals(quemVe)));
    }

    @Transactional
    public Perfil atualizar(UUID id, AlteracaoPerfil alteracao) {
        Usuario usuario = carregar(id);
        String username = Usuario.normalizarUsername(alteracao.username());
        regras.garantirUsernameLivre(username, id);

        usuario.setNomeCompleto(alteracao.nomeCompleto().strip());
        usuario.setUsername(username);
        usuario.setBio(CamposPerfil.vazioParaNulo(alteracao.bio()));
        usuario.setLink(CamposPerfil.normalizarLink(alteracao.link()));
        usuario.setHabilidades(CamposPerfil.normalizarHabilidades(alteracao.habilidades()));
        try {
            usuarios.flush();
        } catch (DataIntegrityViolationException corrida) {
            throw RegrasConta.traduzirDuplicidade(corrida);
        }
        return Perfil.de(usuario, true);
    }

    /** @throws ImagemInvalidaException se o arquivo não for um JPG ou PNG aceitável */
    @Transactional
    public Perfil trocarFoto(UUID id, byte[] conteudo) {
        return trocarImagem(id, conteudo, FormatoImagem.AVATAR, Usuario::getFotoPerfilUrl, Usuario::setFotoPerfilUrl);
    }

    /** @throws ImagemInvalidaException se o arquivo não for um JPG ou PNG aceitável */
    @Transactional
    public Perfil trocarCapa(UUID id, byte[] conteudo) {
        return trocarImagem(id, conteudo, FormatoImagem.CAPA, Usuario::getCapaUrl, Usuario::setCapaUrl);
    }

    /** Grava a imagem nova antes de mexer no banco; a que sobrar é apagada quando a transação terminar. */
    private Perfil trocarImagem(UUID id, byte[] conteudo, FormatoImagem formato, Function<Usuario, String> atual,
            BiConsumer<Usuario, String> definir) {
        Usuario usuario = carregar(id);
        String nova = imagens.salvar(conteudo, formato);
        imagens.descartarAoFim(atual.apply(usuario), nova);
        definir.accept(usuario, nova);
        return Perfil.de(usuario, true);
    }

    private Usuario carregar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }
}
