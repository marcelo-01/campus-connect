package br.com.campusconnect.arquivo.processamento;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Lê e aplica a orientação EXIF de um JPEG.
 *
 * <p>O celular grava a foto sempre "deitada" e anota no EXIF como ela deve ser girada. Como a imagem é
 * regravada sem metadados, a rotação precisa ser aplicada nos pixels; senão a foto aparece de lado.
 */
final class OrientacaoExif {

    static final int NORMAL = 1;

    private static final int TAG_ORIENTACAO = 0x0112;

    private OrientacaoExif() {
    }

    /** Valor de 1 a 8 do campo Orientation do EXIF, ou {@link #NORMAL} se não houver ou estiver corrompido. */
    static int ler(byte[] jpeg) {
        try {
            return lerSemValidar(jpeg);
        } catch (IndexOutOfBoundsException corrompido) {
            return NORMAL;
        }
    }

    private static int lerSemValidar(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }
        int pos = 2;
        while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
            int marcador = b[pos + 1] & 0xFF;
            if (marcador == 0xDA || marcador == 0xD9) {
                break; // começo dos dados da imagem: não há mais metadados
            }
            int tamanho = u16(b, pos + 2, false);
            if (marcador == 0xE1 && ehExif(b, pos + 4)) {
                return orientacaoTiff(b, pos + 10);
            }
            pos += 2 + tamanho;
        }
        return NORMAL;
    }

    private static boolean ehExif(byte[] b, int pos) {
        return b[pos] == 'E' && b[pos + 1] == 'x' && b[pos + 2] == 'i' && b[pos + 3] == 'f'
                && b[pos + 4] == 0 && b[pos + 5] == 0;
    }

    private static int orientacaoTiff(byte[] b, int tiff) {
        boolean littleEndian = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!littleEndian && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
            return NORMAL;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, littleEndian);
        int entradas = u16(b, ifd, littleEndian);
        for (int i = 0; i < entradas; i++) {
            int entrada = ifd + 2 + i * 12;
            if (u16(b, entrada, littleEndian) == TAG_ORIENTACAO) {
                int valor = u16(b, entrada + 8, littleEndian);
                return valor >= 1 && valor <= 8 ? valor : NORMAL;
            }
        }
        return NORMAL;
    }

    /** Gira ou espelha a imagem para que ela fique de pé, como a orientação manda. */
    static BufferedImage aplicar(BufferedImage imagem, int orientacao) {
        if (orientacao == NORMAL) {
            return imagem;
        }
        int largura = imagem.getWidth();
        int altura = imagem.getHeight();
        AffineTransform t = new AffineTransform();
        switch (orientacao) {
            case 2 -> { t.scale(-1, 1); t.translate(-largura, 0); }
            case 3 -> { t.translate(largura, altura); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -altura); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(altura, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-altura, 0); t.translate(0, largura); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, largura); t.rotate(3 * Math.PI / 2); }
            default -> { return imagem; }
        }
        boolean deitada = orientacao >= 5;
        BufferedImage destino = new BufferedImage(deitada ? altura : largura, deitada ? largura : altura,
                BufferedImage.TYPE_INT_RGB);
        // Graphics2D em vez de AffineTransformOp: este falha com o tipo de pixel que o leitor de JPEG devolve.
        Graphics2D g = destino.createGraphics();
        try {
            g.drawImage(imagem, t, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static int u16(byte[] b, int pos, boolean littleEndian) {
        int a = b[pos] & 0xFF;
        int c = b[pos + 1] & 0xFF;
        return littleEndian ? (c << 8) | a : (a << 8) | c;
    }

    private static long u32(byte[] b, int pos, boolean littleEndian) {
        long alto = u16(b, littleEndian ? pos + 2 : pos, littleEndian);
        long baixo = u16(b, littleEndian ? pos : pos + 2, littleEndian);
        return (alto << 16) | baixo;
    }
}
