package br.com.campusconnect.marketplace.api;

import br.com.campusconnect.marketplace.service.DadosAnuncio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A parte {@code dados} do multipart de um anúncio, ao anunciar e ao editar. As fotos novas vêm na parte
 * {@code imagens}.
 *
 * @param imagensMantidas só na edição: as fotos atuais que ficam, na ordem
 */
record AnuncioRequest(
        @NotNull(message = "Escolha se é um serviço ou um produto.")
        String tipo,

        @NotNull(message = "Escolha uma categoria.")
        UUID categoriaId,

        @NotNull(message = "Dê um título ao anúncio.")
        @Size(max = 120, message = "O título pode ter até 120 caracteres.")
        String titulo,

        @Size(max = 3000, message = "A descrição pode ter até 3000 caracteres.")
        String descricao,

        @NotNull(message = "Escolha como você cobra.")
        String modalidadePreco,

        BigDecimal preco,

        List<UUID> bolhaIds,

        List<String> imagensMantidas) {

    AnuncioRequest {
        titulo = titulo == null ? null : titulo.strip();
        descricao = descricao == null ? null : descricao.strip();
    }

    DadosAnuncio paraDados() {
        return new DadosAnuncio(tipo, categoriaId, titulo, descricao, modalidadePreco, preco, bolhaIds);
    }
}
