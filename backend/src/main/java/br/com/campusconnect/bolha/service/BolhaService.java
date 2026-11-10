package br.com.campusconnect.bolha.service;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemInvalidaException;
import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.bolha.model.Bolha;
import br.com.campusconnect.bolha.model.MembroBolha;
import br.com.campusconnect.bolha.model.PapelMembro;
import br.com.campusconnect.bolha.model.TipoBolha;
import br.com.campusconnect.bolha.repository.BolhaRepository;
import br.com.campusconnect.bolha.repository.MembroBolhaRepository;
import br.com.campusconnect.bolha.repository.MembroBolhaRepository.TotalPorBolha;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BolhaService {

    static final String SO_ADMINISTRADOR = "Só administradores podem criar, editar ou excluir bolhas.";

    private final BolhaRepository bolhas;
    private final MembroBolhaRepository membros;
    private final RegrasBolha regras;
    private final UsuarioService usuarios;
    private final ImagemService imagens;

    // ------------------------------------------------------------ leitura

    /** Todas as bolhas, cada uma logo antes das que ficam dentro dela. */
    @Transactional(readOnly = true)
    public List<BolhaResumo> listarTodas(UUID quemVe) {
        return arvore(quemVe).emOrdem().stream().map(Arvore.No::resumo).toList();
    }

    /** Bolhas de um usuário, na mesma ordem: aparecem no perfil e no menu lateral. */
    @Transactional(readOnly = true)
    public List<BolhaResumo> listarDoUsuario(UUID usuarioId, UUID quemVe) {
        Set<UUID> doUsuario = membros.idsDasBolhasDo(usuarioId);
        return arvore(quemVe).emOrdem().stream()
                .filter(no -> doUsuario.contains(no.id()))
                .map(Arvore.No::resumo)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BolhaDetalhe> buscar(String slug, UUID quemVe) {
        return arvore(quemVe).porSlug(slug.strip().toLowerCase(Locale.ROOT)).map(Arvore.No::detalhe);
    }

    /** Quem entrou por último primeiro. Contas suspensas ou desativadas não aparecem. */
    @Transactional(readOnly = true)
    public List<MembroDaBolha> listarMembros(UUID bolhaId) {
        if (!bolhas.existsById(bolhaId)) {
            throw new BolhaNaoEncontradaException(bolhaId);
        }
        Map<UUID, MembroBolha> porUsuario = membros.findTop100ByIdBolhaIdOrderByEntrouEmDesc(bolhaId).stream()
                .collect(Collectors.toMap(m -> m.getId().getUsuarioId(), Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        return usuarios.buscarPublicos(List.copyOf(porUsuario.keySet())).stream()
                .map(usuario -> {
                    MembroBolha membro = porUsuario.get(usuario.id());
                    return new MembroDaBolha(usuario.id(), usuario.username(), usuario.nomeCompleto(),
                            usuario.fotoUrl(), membro.getPapel().name(), membro.getEntrouEm());
                })
                .toList();
    }

    // ------------------------------------------------------------ participação

    /** Entra também nas bolhas acima: quem está no curso está no campus e na geral. */
    @Transactional
    public void entrar(UUID quem, UUID bolhaId) {
        for (Bolha atual = carregar(bolhaId); atual != null; atual = atual.getBolhaPai()) {
            membros.entrar(atual.getId(), quem, PapelMembro.MEMBRO.name());
        }
    }

    /** Sai também das bolhas abaixo: quem sai do campus sai dos cursos dele. */
    @Transactional
    public void sair(UUID quem, UUID bolhaId) {
        if (!bolhas.existsById(bolhaId)) {
            throw new BolhaNaoEncontradaException(bolhaId);
        }
        membros.sair(quem, bolhas.idsDaSubarvore(bolhaId));
    }

    // ------------------------------------------------------------ administração

    @Transactional
    public BolhaDetalhe criar(UUID quem, NovaBolha nova) {
        garantirAdministrador(quem);
        TipoBolha tipo = RegrasBolha.tipo(nova.tipo());
        Bolha pai = nova.bolhaPaiId() == null
                ? null
                : bolhas.findById(nova.bolhaPaiId())
                        .orElseThrow(() -> new CampoInvalidoException("bolhaPaiId", RegrasBolha.PAI_NAO_EXISTE));
        RegrasBolha.garantirPosicao(tipo, pai == null ? null : pai.getTipo());

        String nome = nova.nome().strip();
        Bolha bolha = new Bolha(tipo, nome, regras.gerarSlug(nome), pai);
        preencher(bolha, nome, nova.sigla(), nova.descricao(), nova.endereco());
        gravar(bolha);
        return detalhar(bolha, quem);
    }

    @Transactional
    public BolhaDetalhe atualizar(UUID quem, UUID bolhaId, AlteracaoBolha alteracao) {
        garantirAdministrador(quem);
        Bolha bolha = carregar(bolhaId);
        preencher(bolha, alteracao.nome().strip(), alteracao.sigla(), alteracao.descricao(), alteracao.endereco());
        gravar(bolha);
        return detalhar(bolha, quem);
    }

    /** @throws ImagemInvalidaException se o arquivo não for um JPG ou PNG aceitável */
    @Transactional
    public BolhaDetalhe trocarFoto(UUID quem, UUID bolhaId, byte[] conteudo) {
        garantirAdministrador(quem);
        Bolha bolha = carregar(bolhaId);
        String nova = imagens.salvar(conteudo, FormatoImagem.AVATAR);
        imagens.descartarAoFim(bolha.getFotoUrl(), nova);
        bolha.setFotoUrl(nova);
        return detalhar(bolha, quem);
    }

    /**
     * Os membros saem junto (cascade no banco) e a foto é apagada depois do commit.
     *
     * @throws BolhaComSubBolhasException se ainda houver bolhas dentro desta
     */
    @Transactional
    public void excluir(UUID quem, UUID bolhaId) {
        garantirAdministrador(quem);
        Bolha bolha = carregar(bolhaId);
        if (bolhas.existsByBolhaPaiId(bolhaId)) {
            throw new BolhaComSubBolhasException();
        }
        imagens.descartarAoFim(bolha.getFotoUrl(), null);
        bolhas.delete(bolha);
        try {
            bolhas.flush();
        } catch (DataIntegrityViolationException corrida) {
            throw RegrasBolha.traduzirExclusao(corrida);
        }
    }

    // ------------------------------------------------------------ apoio

    private void garantirAdministrador(UUID quem) {
        if (!usuarios.ehAdministrador(quem)) {
            throw new AcessoNegadoException(SO_ADMINISTRADOR);
        }
    }

    private static void preencher(Bolha bolha, String nome, String sigla, String descricao, String endereco) {
        bolha.setNome(nome);
        bolha.setSigla(RegrasBolha.vazioParaNulo(sigla));
        bolha.setDescricao(RegrasBolha.vazioParaNulo(descricao));
        bolha.setEndereco(RegrasBolha.vazioParaNulo(endereco));
    }

    private void gravar(Bolha bolha) {
        try {
            bolhas.saveAndFlush(bolha);
        } catch (DataIntegrityViolationException corrida) {
            throw RegrasBolha.traduzirDuplicidade(corrida);
        }
    }

    private Bolha carregar(UUID id) {
        return bolhas.findById(id).orElseThrow(() -> new BolhaNaoEncontradaException(id));
    }

    private BolhaDetalhe detalhar(Bolha bolha, UUID quemVe) {
        return arvore(quemVe).porId(bolha.getId()).map(Arvore.No::detalhe).orElseThrow();
    }

    private Arvore arvore(UUID quemVe) {
        Map<UUID, Long> totais = membros.contarPorBolha().stream()
                .collect(Collectors.toMap(TotalPorBolha::getBolhaId, TotalPorBolha::getTotal));
        return Arvore.montar(bolhas.findAll(), totais, membros.idsDasBolhasDo(quemVe));
    }
}
