package br.com.campusconnect.arquivo.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Grava vídeos MP4 ou WebM como vieram, sem converter. O navegador toca os dois no {@code <video>}.
 *
 * <p>O tipo é conferido pelos primeiros bytes, não pelo nome nem pelo Content-Type que o navegador
 * mandou: um MP4 tem {@code ftyp} a partir do quinto byte, e um WebM começa com a marca EBML
 * {@code 1A 45 DF A3}.
 */
@Service
@RequiredArgsConstructor
public class VideoService {

    public static final long TAMANHO_MAXIMO = 50L * 1024 * 1024;

    static final String GRANDE_DEMAIS = "Envie um vídeo de até 50 MB.";
    static final String FORMATO_INVALIDO = "Envie um vídeo MP4 ou WebM.";

    private static final int TAMANHO_CABECALHO = 12;

    private final ArquivoService arquivos;

    /**
     * @param tamanhoDeclarado tamanho informado pelo upload; o limite também é conferido durante a cópia
     * @return caminho público do vídeo gravado ({@code /api/arquivos/<nome>.mp4} ou {@code .webm})
     * @throws VideoInvalidoException se não for MP4 nem WebM, ou se passar de 50 MB
     */
    public String salvar(InputStream conteudo, long tamanhoDeclarado) {
        if (tamanhoDeclarado > TAMANHO_MAXIMO) {
            throw new VideoInvalidoException(GRANDE_DEMAIS);
        }
        BufferedInputStream entrada = new BufferedInputStream(conteudo);
        String extensao = extensao(espiar(entrada));
        return arquivos.gravar(entrada, extensao, TAMANHO_MAXIMO, () -> new VideoInvalidoException(GRANDE_DEMAIS));
    }

    static String extensao(byte[] cabecalho) {
        if (cabecalho.length >= 8
                && cabecalho[4] == 'f' && cabecalho[5] == 't' && cabecalho[6] == 'y' && cabecalho[7] == 'p') {
            return "mp4";
        }
        if (cabecalho.length >= 4
                && (cabecalho[0] & 0xFF) == 0x1A && (cabecalho[1] & 0xFF) == 0x45
                && (cabecalho[2] & 0xFF) == 0xDF && (cabecalho[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        throw new VideoInvalidoException(FORMATO_INVALIDO);
    }

    private static byte[] espiar(BufferedInputStream entrada) {
        try {
            entrada.mark(TAMANHO_CABECALHO);
            byte[] cabecalho = entrada.readNBytes(TAMANHO_CABECALHO);
            entrada.reset();
            return cabecalho;
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível ler o vídeo enviado", erro);
        }
    }
}
