package br.com.campusconnect.conexao.service;

import br.com.campusconnect.conexao.model.Conexao;
import br.com.campusconnect.conexao.model.StatusConexao;
import br.com.campusconnect.conexao.repository.ConexaoRepository;
import br.com.campusconnect.conexao.service.RegrasConexao.Par;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import br.com.campusconnect.usuario.service.UsuarioService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos e conexões entre usuários.
 *
 * <p>As ações recebem quem age e a outra pessoa e devolvem como a relação ficou para quem agiu. Repetir uma ação
 * não dá erro, e uma tela desatualizada se acerta com a resposta. Cada intenção tem a sua ação, com a sua condição
 * (ver {@link ConexaoRepository}): cancelar um pedido nunca desfaz uma conexão, e desfazer nunca cancela um pedido.
 *
 * <p>Contas que não aparecem para os outros (suspensas, banidas, desativadas ou anonimizadas) ficam fora das listas
 * e do total, como na lista de membros de uma bolha.
 */
@Service
@RequiredArgsConstructor
public class ConexaoService {

    private static final Comparator<PessoaNaConexao> MAIS_RECENTES_PRIMEIRO =
            Comparator.comparing(PessoaNaConexao::desde).reversed();

    private final ConexaoRepository conexoes;
    private final UsuarioService usuarios;

    // ------------------------------------------------------------ ações

    /**
     * Pede a conexão. Se a outra pessoa já tinha pedido, a conexão sai na hora, porque as duas querem. Vale até
     * quando {@code quem} tinha recusado esse pedido antes: para a outra pessoa, ele continuava enviado.
     *
     * @throws ConexaoConsigoMesmoException se {@code quem} e {@code outro} forem a mesma pessoa
     * @throws PessoaNaoEncontradaException se a outra conta não existe ou não aparece para os outros
     */
    @Transactional
    public SituacaoConexao conectar(UUID quem, UUID outro) {
        Par par = Par.de(quem, outro);
        if (usuarios.buscarPublicos(List.of(outro)).isEmpty()) {
            throw new PessoaNaoEncontradaException(outro);
        }
        if (conexoes.pedir(par.menor(), par.maior(), quem) == 0) {
            conexoes.aceitarPedidoDe(par.menor(), par.maior(), outro);
        }
        return situacao(quem, par);
    }

    /**
     * Recusa um pedido pendente da outra pessoa. Ela não é avisada: para ela, o pedido continua enviado.
     *
     * @throws ConexaoConsigoMesmoException se {@code quem} e {@code outro} forem a mesma pessoa
     */
    @Transactional
    public SituacaoConexao recusar(UUID quem, UUID outro) {
        Par par = Par.de(quem, outro);
        conexoes.recusarPedidoDe(par.menor(), par.maior(), outro);
        return situacao(quem, par);
    }

    /**
     * Retira um pedido que {@code quem} fez, inclusive um recusado em silêncio.
     *
     * @throws ConexaoConsigoMesmoException se {@code quem} e {@code outro} forem a mesma pessoa
     */
    @Transactional
    public SituacaoConexao cancelarPedido(UUID quem, UUID outro) {
        Par par = Par.de(quem, outro);
        conexoes.cancelarPedidoDe(par.menor(), par.maior(), quem);
        return situacao(quem, par);
    }

    /**
     * Desfaz uma conexão aceita. Qualquer um dos dois pode desfazer, e depois qualquer um pode pedir de novo.
     *
     * @throws ConexaoConsigoMesmoException se {@code quem} e {@code outro} forem a mesma pessoa
     */
    @Transactional
    public SituacaoConexao desfazer(UUID quem, UUID outro) {
        Par par = Par.de(quem, outro);
        conexoes.desfazer(par.menor(), par.maior());
        return situacao(quem, par);
    }

    // ------------------------------------------------------------ leitura

    /** Conexões, pedidos recebidos e pedidos enviados de quem está logado. */
    @Transactional(readOnly = true)
    public MinhasConexoes minhas(UUID quem) {
        List<Conexao> registros = conexoes.todasDoUsuario(quem);
        Map<UUID, UsuarioPublico> pessoas = publicosPorId(registros, quem);

        List<PessoaNaConexao> conectadas = new ArrayList<>();
        List<PessoaNaConexao> recebidos = new ArrayList<>();
        List<PessoaNaConexao> enviados = new ArrayList<>();
        for (Conexao registro : registros) {
            UsuarioPublico pessoa = pessoas.get(outroLado(registro, quem));
            if (pessoa == null) {
                continue;
            }
            switch (RegrasConexao.vistaPor(quem, registro.getStatus(), registro.getSolicitanteId())) {
                case CONECTADOS -> conectadas.add(naConexao(pessoa, conectadosDesde(registro)));
                case PEDIDO_RECEBIDO -> recebidos.add(naConexao(pessoa, registro.getSolicitadoEm()));
                case PEDIDO_ENVIADO -> enviados.add(naConexao(pessoa, registro.getSolicitadoEm()));
                case NENHUMA -> {
                    // pedido que quem está logado recusou: não volta para a lista
                }
            }
        }
        conectadas.sort(MAIS_RECENTES_PRIMEIRO);
        recebidos.sort(MAIS_RECENTES_PRIMEIRO);
        enviados.sort(MAIS_RECENTES_PRIMEIRO);
        return new MinhasConexoes(conectadas, recebidos, enviados);
    }

    /**
     * Conexões aceitas de qualquer pessoa, as mais recentes primeiro. Qualquer pessoa logada pode ver.
     *
     * @throws PessoaNaoEncontradaException se a conta não aparece para os outros e não é de quem está vendo
     */
    @Transactional(readOnly = true)
    public List<PessoaNaConexao> listarConexoes(UUID usuarioId, UUID quemVe) {
        if (!usuarioId.equals(quemVe) && usuarios.buscarPublicos(List.of(usuarioId)).isEmpty()) {
            throw new PessoaNaoEncontradaException(usuarioId);
        }
        List<Conexao> aceitas = conexoes.todasDoUsuario(usuarioId).stream()
                .filter(registro -> registro.getStatus() == StatusConexao.ACEITA)
                .toList();
        Map<UUID, UsuarioPublico> pessoas = publicosPorId(aceitas, usuarioId);
        return aceitas.stream()
                .filter(registro -> pessoas.containsKey(outroLado(registro, usuarioId)))
                .map(registro -> naConexao(pessoas.get(outroLado(registro, usuarioId)), conectadosDesde(registro)))
                .sorted(MAIS_RECENTES_PRIMEIRO)
                .toList();
    }

    /**
     * O tamanho da lista de {@link #listarConexoes}, para o número do perfil bater com a lista que ele abre.
     *
     * @throws PessoaNaoEncontradaException se a conta não aparece para os outros e não é de quem está vendo
     */
    @Transactional(readOnly = true)
    public long contarConexoes(UUID usuarioId, UUID quemVe) {
        return listarConexoes(usuarioId, quemVe).size();
    }

    // ------------------------------------------------------------ apoio

    private SituacaoConexao situacao(UUID quem, Par par) {
        return conexoes.estadoDoPar(par.menor(), par.maior())
                .map(estado -> RegrasConexao.vistaPor(quem, estado.getStatus(), estado.getSolicitanteId()))
                .orElse(SituacaoConexao.NENHUMA);
    }

    /** Quem está do outro lado de cada registro, só entre as contas que aparecem para os outros. */
    private Map<UUID, UsuarioPublico> publicosPorId(List<Conexao> registros, UUID usuarioId) {
        List<UUID> ids = registros.stream().map(registro -> outroLado(registro, usuarioId)).toList();
        return usuarios.buscarPublicos(ids).stream()
                .collect(Collectors.toMap(UsuarioPublico::id, Function.identity()));
    }

    private static UUID outroLado(Conexao registro, UUID usuarioId) {
        return registro.getUsuarioMenorId().equals(usuarioId) ? registro.getUsuarioMaiorId()
                : registro.getUsuarioMenorId();
    }

    /** Quando o pedido foi aceito. Um registro gravado sem essa data, direto no banco, usa a data do pedido. */
    private static Instant conectadosDesde(Conexao registro) {
        return registro.getRespondidoEm() != null ? registro.getRespondidoEm() : registro.getSolicitadoEm();
    }

    private static PessoaNaConexao naConexao(UsuarioPublico pessoa, Instant desde) {
        return new PessoaNaConexao(pessoa.id(), pessoa.username(), pessoa.nomeCompleto(), pessoa.fotoUrl(), desde);
    }
}
