package br.com.campusconnect.publicacao.service;

import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que acontece com publicações e comentários e interessa a outros módulos, como o de notificações. Sai dentro da
 * transação de quem agiu, e só quando algo mudou: curtir duas vezes publica uma vez só.
 *
 * <p>Salvar não publica nada, porque é privado.
 */
public final class EventosPublicacao {

    private EventosPublicacao() {
    }

    public record PublicacaoCurtida(UUID quem, UUID publicacaoId, UUID autorId) {
    }

    public record CurtidaRetirada(UUID quem, UUID publicacaoId) {
    }

    public record PublicacaoRepublicada(UUID quem, UUID publicacaoId, UUID autorId) {
    }

    public record RepublicacaoDesfeita(UUID quem, UUID publicacaoId) {
    }

    /**
     * Um comentário novo.
     *
     * @param autorDaPublicacao quem escreveu a publicação comentada
     * @param respondendoA quem escreveu o comentário a que este responde; {@code null} se não é resposta
     * @param conteudo o texto inteiro do comentário
     */
    public record ComentarioFeito(UUID comentarioId, UUID publicacaoId, UUID autorId, String conteudo,
            UUID autorDaPublicacao, @Nullable UUID respondendoA) {
    }

    /** @param conteudo o texto inteiro do comentário curtido */
    public record ComentarioCurtido(UUID quem, UUID comentarioId, UUID publicacaoId, UUID autorDoComentario,
            String conteudo) {
    }

    public record CurtidaDeComentarioRetirada(UUID quem, UUID comentarioId) {
    }

    /** O comentário foi excluído. As respostas a ele continuam. */
    public record ComentarioExcluido(UUID comentarioId) {
    }

    /**
     * As publicações saíram do ar: excluídas por quem escreveu ou por um administrador, ou apagadas junto com a
     * bolha. Os comentários delas saem junto, sem um {@link ComentarioExcluido} para cada um.
     */
    public record PublicacoesExcluidas(Collection<UUID> publicacaoIds) {
    }
}
