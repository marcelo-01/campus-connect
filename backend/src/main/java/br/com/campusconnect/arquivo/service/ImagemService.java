package br.com.campusconnect.arquivo.service;

import br.com.campusconnect.arquivo.processamento.ProcessadorImagem;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Valida e ajusta imagens antes de gravar na pasta de arquivos. Devolve o caminho público
 * ({@code /api/arquivos/<nome>.jpg}), que os outros módulos gravam no banco.
 */
@Service
@RequiredArgsConstructor
public class ImagemService {

    public static final String PREFIXO_URL = ArquivoService.PREFIXO_URL;

    /**
     * O limite de upload do servidor é o do vídeo, então o das imagens é conferido aqui. Quem recebe um
     * {@code MultipartFile} pode conferir antes de ler os bytes.
     */
    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    static final String GRANDE_DEMAIS = "Envie uma imagem de até 5 MB.";

    private final ProcessadorImagem processador;
    private final ArquivoService arquivos;

    /**
     * Valida, ajusta e grava a imagem.
     *
     * @return caminho público da imagem gravada
     * @throws ImagemInvalidaException se o conteúdo não for um JPG ou PNG aceitável ou passar de 5 MB
     */
    public String salvar(byte[] conteudo, FormatoImagem formato) {
        garantirTamanho(conteudo.length);
        return arquivos.gravar(processador.processar(conteudo, formato), "jpg");
    }

    /** @throws ImagemInvalidaException se passar de 5 MB */
    public static void garantirTamanho(long tamanho) {
        if (tamanho > TAMANHO_MAXIMO) {
            throw new ImagemInvalidaException(GRANDE_DEMAIS);
        }
    }

    /** Veja {@link ArquivoService#remover}. */
    public void remover(@Nullable String url) {
        arquivos.remover(url);
    }

    /** Veja {@link ArquivoService#descartarAoFim(String, String)}. */
    public void descartarAoFim(@Nullable String seConfirmar, @Nullable String seDesfazer) {
        arquivos.descartarAoFim(seConfirmar, seDesfazer);
    }
}
