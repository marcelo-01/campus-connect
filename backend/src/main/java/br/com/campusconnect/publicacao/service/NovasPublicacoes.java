package br.com.campusconnect.publicacao.service;

import br.com.campusconnect.usuario.service.UsuarioPublico;
import java.util.List;

/**
 * O aviso de posts novos no topo do feed.
 *
 * @param total quantos posts novos há, contados até 20
 * @param mais se passou de 20
 * @param autores quem publicou, até 3, de quem publicou por último para quem publicou antes
 */
public record NovasPublicacoes(int total, boolean mais, List<UsuarioPublico> autores) {
}
