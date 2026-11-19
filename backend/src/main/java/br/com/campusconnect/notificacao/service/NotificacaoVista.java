package br.com.campusconnect.notificacao.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uma notificação na tela. As curtidas e as republicações do mesmo alvo vêm juntas, numa só.
 *
 * @param id estável entre páginas e recargas
 * @param tipo {@code SOLICITACAO_CONEXAO}, {@code CONEXAO_ACEITA}, {@code NOVO_COMENTARIO},
 *     {@code RESPOSTA_COMENTARIO}, {@code CURTIDA_PUBLICACAO}, {@code CURTIDA_COMENTARIO}, {@code REPUBLICACAO} ou
 *     {@code AVALIACAO_ANUNCIO}
 * @param em quando chegou a mais nova do grupo
 * @param nova se alguma do grupo ainda não tinha sido lida
 * @param pessoas quem fez, da mais recente para a mais antiga, até {@value NotificacaoService#PESSOAS_POR_GRUPO}
 * @param total quantas pessoas fizeram, contando as que não estão em {@code pessoas}
 * @param publicacaoId para onde levam as de publicação e as de comentário
 * @param anuncioId para onde leva a de avaliação
 * @param trecho o comentário, nas de comentário; o começo da publicação, nas curtidas e republicações dela; o título
 *     do anúncio, na avaliação; {@code null} nas de conexão
 */
public record NotificacaoVista(String id, String tipo, Instant em, boolean nova, List<UsuarioPublico> pessoas,
        long total, @Nullable UUID publicacaoId, @Nullable UUID anuncioId, @Nullable String trecho) {
}
