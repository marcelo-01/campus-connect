package br.com.campusconnect.publicacao.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Curtidas, publicações salvas e republicações, e as contagens que o feed mostra. Ligar e desligar são
 * idempotentes: dois cliques seguidos não dão erro nem contam duas vezes.
 *
 * <p>As contagens e os estados de quem vê são lidos em lote, para a página inteira do feed de uma vez.
 */
@Repository
@RequiredArgsConstructor
public class InteracaoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------ curtidas

    public void curtirPublicacao(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("""
                insert into reacao (usuario_id, publicacao_id, tipo) values (:usuario, :alvo, 'CURTIDA')
                on conflict do nothing
                """, parametros(usuarioId, publicacaoId));
    }

    public void descurtirPublicacao(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("delete from reacao where usuario_id = :usuario and publicacao_id = :alvo",
                parametros(usuarioId, publicacaoId));
    }

    public void curtirComentario(UUID usuarioId, UUID comentarioId) {
        jdbc.update("""
                insert into reacao (usuario_id, comentario_id, tipo) values (:usuario, :alvo, 'CURTIDA')
                on conflict do nothing
                """, parametros(usuarioId, comentarioId));
    }

    public void descurtirComentario(UUID usuarioId, UUID comentarioId) {
        jdbc.update("delete from reacao where usuario_id = :usuario and comentario_id = :alvo",
                parametros(usuarioId, comentarioId));
    }

    // ------------------------------------------------------------ salvas e republicações

    public void salvar(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("""
                insert into publicacao_salva (usuario_id, publicacao_id) values (:usuario, :alvo)
                on conflict do nothing
                """, parametros(usuarioId, publicacaoId));
    }

    public void deixarDeSalvar(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("delete from publicacao_salva where usuario_id = :usuario and publicacao_id = :alvo",
                parametros(usuarioId, publicacaoId));
    }

    public void republicar(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("""
                insert into republicacao (usuario_id, publicacao_id) values (:usuario, :alvo)
                on conflict do nothing
                """, parametros(usuarioId, publicacaoId));
    }

    public void desfazerRepublicacao(UUID usuarioId, UUID publicacaoId) {
        jdbc.update("delete from republicacao where usuario_id = :usuario and publicacao_id = :alvo",
                parametros(usuarioId, publicacaoId));
    }

    // ------------------------------------------------------------ contagens

    public Map<UUID, Long> curtidasPorPublicacao(Collection<UUID> publicacoes) {
        return contar("select publicacao_id, count(*) from reacao where publicacao_id in (:ids) group by 1",
                publicacoes);
    }

    /** Só os comentários que não foram excluídos. */
    public Map<UUID, Long> comentariosPorPublicacao(Collection<UUID> publicacoes) {
        return contar("""
                select publicacao_id, count(*) from comentario
                where publicacao_id in (:ids) and removido_em is null group by 1
                """, publicacoes);
    }

    public Map<UUID, Long> republicacoesPorPublicacao(Collection<UUID> publicacoes) {
        return contar("select publicacao_id, count(*) from republicacao where publicacao_id in (:ids) group by 1",
                publicacoes);
    }

    public Map<UUID, Long> curtidasPorComentario(Collection<UUID> comentarios) {
        return contar("select comentario_id, count(*) from reacao where comentario_id in (:ids) group by 1",
                comentarios);
    }

    // ------------------------------------------------------------ o que quem vê já fez

    public Set<UUID> curtidasPor(UUID usuarioId, Collection<UUID> publicacoes) {
        return dentre("select publicacao_id from reacao where usuario_id = :usuario and publicacao_id in (:ids)",
                usuarioId, publicacoes);
    }

    public Set<UUID> salvasPor(UUID usuarioId, Collection<UUID> publicacoes) {
        return dentre("""
                select publicacao_id from publicacao_salva where usuario_id = :usuario and publicacao_id in (:ids)
                """, usuarioId, publicacoes);
    }

    public Set<UUID> republicadasPor(UUID usuarioId, Collection<UUID> publicacoes) {
        return dentre("""
                select publicacao_id from republicacao where usuario_id = :usuario and publicacao_id in (:ids)
                """, usuarioId, publicacoes);
    }

    public Set<UUID> comentariosCurtidosPor(UUID usuarioId, Collection<UUID> comentarios) {
        return dentre("select comentario_id from reacao where usuario_id = :usuario and comentario_id in (:ids)",
                usuarioId, comentarios);
    }

    // ------------------------------------------------------------ exclusão de uma bolha

    /** Os arquivos (imagens, vídeos e imagens de prévia) de todas as publicações da bolha, até das excluídas. */
    public List<String> arquivosDasPublicacoesDaBolha(UUID bolhaId) {
        return jdbc.queryForList("""
                select m.url from midia_publicacao m join publicacao p on p.id = m.publicacao_id
                where p.bolha_id = :bolha and m.tipo in ('IMAGEM', 'VIDEO')
                union all
                select m.imagem_url from midia_publicacao m join publicacao p on p.id = m.publicacao_id
                where p.bolha_id = :bolha and m.imagem_url is not null
                """, new MapSqlParameterSource("bolha", bolhaId), String.class);
    }

    /**
     * Apaga de vez as publicações da bolha, com tudo o que depende delas. As mídias saem junto pelo
     * {@code ON DELETE CASCADE}; o resto vai antes, na ordem das chaves estrangeiras.
     */
    public void apagarPublicacoesDaBolha(UUID bolhaId) {
        MapSqlParameterSource bolha = new MapSqlParameterSource("bolha", bolhaId);
        String daBolha = "select id from publicacao where bolha_id = :bolha";
        jdbc.update("delete from reacao where comentario_id in (select id from comentario where publicacao_id in ("
                + daBolha + "))", bolha);
        jdbc.update("delete from reacao where publicacao_id in (" + daBolha + ")", bolha);
        jdbc.update("delete from comentario where publicacao_id in (" + daBolha + ")", bolha);
        jdbc.update("delete from publicacao_salva where publicacao_id in (" + daBolha + ")", bolha);
        jdbc.update("delete from republicacao where publicacao_id in (" + daBolha + ")", bolha);
        jdbc.update("delete from publicacao where bolha_id = :bolha", bolha);
    }

    // ------------------------------------------------------------ apoio

    private static MapSqlParameterSource parametros(UUID usuarioId, UUID alvoId) {
        return new MapSqlParameterSource("usuario", usuarioId).addValue("alvo", alvoId);
    }

    private Map<UUID, Long> contar(String sql, Collection<UUID> ids) {
        Map<UUID, Long> totais = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query(sql, new MapSqlParameterSource("ids", ids),
                    linha -> {
                        totais.put(linha.getObject(1, UUID.class), linha.getLong(2));
                    });
        }
        return totais;
    }

    private Set<UUID> dentre(String sql, UUID usuarioId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(sql, new MapSqlParameterSource("usuario", usuarioId)
                .addValue("ids", ids), UUID.class));
    }
}
