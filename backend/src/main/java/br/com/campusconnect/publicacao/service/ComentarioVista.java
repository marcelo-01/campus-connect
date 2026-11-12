package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Um comentário com as respostas dele. As respostas têm um nível só, então a lista delas vem sempre vazia.
 *
 * @param removido comentário excluído que continua na lista porque tem respostas; vem sem autor e sem texto
 * @param curtido se quem vê curtiu
 */
public record ComentarioVista(UUID id, @Nullable UsuarioPublico autor, @Nullable String conteudo,
        Instant criadoEm, long curtidas, boolean curtido, boolean removido, boolean podeExcluir,
        List<ComentarioVista> respostas) {
}
