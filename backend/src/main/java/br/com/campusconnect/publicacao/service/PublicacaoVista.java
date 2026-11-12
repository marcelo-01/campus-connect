package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.bolha.service.BolhaResumo;
import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uma publicação como aparece no feed, já do ponto de vista de quem está vendo.
 *
 * @param bolha a bolha em que o post foi feito, que pode ser uma acima da que a pessoa está vendo: o que
 *     é publicado numa bolha desce para as de dentro dela
 * @param midias imagens (até 4) ou um vídeo, na ordem em que foram anexados
 * @param link o link anexado, com a prévia quando o servidor conseguiu ler a página
 * @param editadoEm quando o texto foi editado pela última vez, ou {@code null} se nunca foi
 * @param curtida se quem vê curtiu
 * @param salva se quem vê salvou
 * @param republicada se quem vê republicou
 * @param podeInteragir se quem vê é membro da bolha do post e pode curtir, comentar e republicar
 */
public record PublicacaoVista(UUID id, UsuarioPublico autor, BolhaResumo bolha, String conteudo,
        List<MidiaVista> midias, @Nullable LinkVista link, Instant criadoEm, @Nullable Instant editadoEm,
        long curtidas, long comentarios, long republicacoes, boolean curtida, boolean salva,
        boolean republicada, boolean podeInteragir, boolean podeEditar, boolean podeExcluir) {

    /** @param tipo {@code IMAGEM} ou {@code VIDEO} */
    public record MidiaVista(String tipo, String url, @Nullable String mimeType) {
    }

    /**
     * Sem prévia, só o {@code url} vem preenchido e o frontend mostra o domínio.
     *
     * @param imagemUrl cópia da imagem de destaque da página, gravada em {@code /api/arquivos}
     */
    public record LinkVista(String url, @Nullable String titulo, @Nullable String descricao,
            @Nullable String site, @Nullable String imagemUrl) {
    }
}
