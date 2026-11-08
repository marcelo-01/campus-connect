package br.com.campusconnect.arquivo.processamento;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemInvalidaException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Transforma o arquivo enviado numa imagem JPEG nova.
 *
 * <p>Regravar em vez de guardar o arquivo original tem três efeitos:
 * <ul>
 *   <li>só passa o que o Java consegue decodificar como JPEG ou PNG; um arquivo que só tem extensão
 *       de imagem é recusado;</li>
 *   <li>os metadados somem, inclusive a localização GPS que o celular grava na foto (LGPD);</li>
 *   <li>a imagem já sai no tamanho em que vai ser exibida.</li>
 * </ul>
 */
@Component
public class ProcessadorImagem {

    private static final Set<String> FORMATOS_ACEITOS = Set.of("jpeg", "png");

    /** Barra "bombas de descompressão": um PNG pequeno em bytes que vira gigabytes ao abrir. */
    private static final long MAXIMO_PIXELS = 40_000_000L;
    private static final int MAXIMO_LADO = 10_000;

    private static final float QUALIDADE_JPEG = 0.85f;

    public byte[] processar(byte[] conteudo, FormatoImagem formato) {
        Lida lida = ler(conteudo);
        BufferedImage imagem = "jpeg".equals(lida.formato())
                ? OrientacaoExif.aplicar(lida.imagem(), OrientacaoExif.ler(conteudo))
                : lida.imagem();
        return codificarJpeg(ajustar(imagem, formato));
    }

    private record Lida(BufferedImage imagem, String formato) {
    }

    private static Lida ler(byte[] conteudo) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(conteudo))) {
            Iterator<ImageReader> leitores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (leitores == null || !leitores.hasNext()) {
                throw new ImagemInvalidaException("O arquivo não é uma imagem. Envie um JPG ou PNG.");
            }
            ImageReader leitor = leitores.next();
            try {
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATOS_ACEITOS.contains(formato)) {
                    throw new ImagemInvalidaException("Formato não aceito. Envie um JPG ou PNG.");
                }
                leitor.setInput(entrada, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura > MAXIMO_LADO || altura > MAXIMO_LADO || (long) largura * altura > MAXIMO_PIXELS) {
                    throw new ImagemInvalidaException("A imagem tem resolução grande demais.");
                }
                return new Lida(leitor.read(0), formato);
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException ilegivel) {
            if (ilegivel instanceof ImagemInvalidaException jaExplicada) {
                throw jaExplicada;
            }
            throw new ImagemInvalidaException("Não foi possível ler a imagem. Tente outro arquivo JPG ou PNG.");
        }
    }

    /** Recorta (se o formato for quadrado) e reduz até o lado máximo. Nunca amplia. */
    static BufferedImage ajustar(BufferedImage imagem, FormatoImagem formato) {
        int x = 0;
        int y = 0;
        int largura = imagem.getWidth();
        int altura = imagem.getHeight();
        if (formato.quadrado()) {
            int lado = Math.min(largura, altura);
            x = (largura - lado) / 2;
            y = (altura - lado) / 2;
            largura = lado;
            altura = lado;
        }
        double escala = Math.min(1.0, (double) formato.ladoMaximo() / Math.max(largura, altura));
        int larguraFinal = Math.max(1, (int) Math.round(largura * escala));
        int alturaFinal = Math.max(1, (int) Math.round(altura * escala));

        BufferedImage atual = imagem.getSubimage(x, y, largura, altura);
        // Reduzir pela metade em etapas dá um resultado bem mais nítido que um salto só.
        while (atual.getWidth() / 2 >= larguraFinal && atual.getHeight() / 2 >= alturaFinal) {
            atual = desenhar(atual, atual.getWidth() / 2, atual.getHeight() / 2);
        }
        return desenhar(atual, larguraFinal, alturaFinal);
    }

    /** Desenha sobre fundo branco em RGB: o JPEG não tem transparência. */
    private static BufferedImage desenhar(BufferedImage origem, int largura, int altura) {
        BufferedImage destino = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largura, altura);
            g.drawImage(origem, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] codificarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(QUALIDADE_JPEG);
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha ao gravar o JPEG em memória", erro);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
