package br.com.campusconnect.bolha.service;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemInvalidaException;
import br.com.campusconnect.arquivo.service.ImagemService;
import br.com.campusconnect.bolha.model.Bolha;
import br.com.campusconnect.bolha.model.MembroBolha;
import br.com.campusconnect.bolha.model.MembroBolhaId;
import br.com.campusconnect.bolha.model.PapelMembro;
import br.com.campusconnect.bolha.model.TipoBolha;
import br.com.campusconnect.bolha.repository.BolhaRepository;
import br.com.campusconnect.bolha.repository.MembroBolhaRepository;
import br.com.campusconnect.bolha.repository.MembroBolhaRepository.TotalPorBolha;
import br.com.campusconnect.comum.AcessoNegadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventos;

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

    // ------------------------------------------------------------ para os outros módulos

    /** Bolhas em que a pessoa participa. */
    @Transactional(readOnly = true)
    public Set<UUID> idsDasBolhasDo(UUID usuarioId) {
        return membros.idsDasBolhasDo(usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean ehMembro(UUID usuarioId, UUID bolhaId) {
        return membros.existsById(new MembroBolhaId(bolhaId, usuarioId));
    }

    /**
     * A bolha e as que ficam acima dela, de baixo para cima. É o que o feed de uma bolha mostra: o que foi
     * publicado numa bolha desce para as de dentro dela, mas não sobe.
     *
     * @throws BolhaNaoEncontradaException se a bolha não existir
     */
    @Transactional(readOnly = true)
    public List<UUID> idsDoCaminho(UUID bolhaId) {
        List<UUID> ids = new ArrayList<>();
        for (Bolha atual = carregar(bolhaId); atual != null; atual = atual.getBolhaPai()) {
            ids.add(atual.getId());
        }
        return ids;
    }

    /** Resumo de cada bolha pedida, com {@code membro} do ponto de vista de quem vê. Ids que não existem ficam de fora. */
    @Transactional(readOnly = true)
    public Map<UUID, BolhaResumo> resumos(Collection<UUID> ids, UUID quemVe) {
        Arvore arvore = arvore(quemVe);
        Map<UUID, BolhaResumo> resumos = new HashMap<>();
        for (UUID id : ids) {
            arvore.porId(id).ifPresent(no -> resumos.put(id, no.resumo()));
        }
        return resumos;
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
     * Os membros saem junto (cascade no banco) e a foto é apagada depois do commit. Antes de apagar, avisa
     * com {@link BolhaExcluida}, na mesma transação, para os outros módulos apagarem o que é da bolha
     * (as publicações, por exemplo).
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
        eventos.publishEvent(new BolhaExcluida(bolhaId));
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
