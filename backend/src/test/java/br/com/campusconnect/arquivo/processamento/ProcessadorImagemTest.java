package br.com.campusconnect.arquivo.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.campusconnect.arquivo.service.FormatoImagem;
import br.com.campusconnect.arquivo.service.ImagemInvalidaException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem();

    @Test
    void avatarSaiQuadradoEReduzido() throws IOException {
        BufferedImage saida = processar(png(1000, 600), FormatoImagem.AVATAR);

        assertThat(saida.getWidth()).isEqualTo(512);
        assertThat(saida.getHeight()).isEqualTo(512);
    }

    @Test
    void capaMantemAProporcao() throws IOException {
        BufferedImage saida = processar(png(3200, 1000), FormatoImagem.CAPA);

        assertThat(saida.getWidth()).isEqualTo(1600);
        assertThat(saida.getHeight()).isEqualTo(500);
    }

    @Test
    void imagemPequenaNaoEhAmpliada() throws IOException {
        assertThat(processar(png(100, 50), FormatoImagem.CAPA).getWidth()).isEqualTo(100);
        BufferedImage avatar = processar(png(100, 50), FormatoImagem.AVATAR);
        assertThat(avatar.getWidth()).isEqualTo(50);
        assertThat(avatar.getHeight()).isEqualTo(50);
    }

    @Test
    void saiSempreEmJpegSemOsMetadadosDoOriginal() throws IOException {
        byte[] comExif = comOrientacaoExif(jpeg(200, 100), 1);
        assertThat(new String(comExif, StandardCharsets.ISO_8859_1)).contains("Exif");

        byte[] saida = processador.processar(comExif, FormatoImagem.CAPA);

        assertThat(saida[0] & 0xFF).isEqualTo(0xFF);
        assertThat(saida[1] & 0xFF).isEqualTo(0xD8);
        assertThat(new String(saida, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    /** Orientação 6: a câmera gravou deitada e a foto precisa girar 90° no sentido horário. */
    @Test
    void giraAFotoConformeAOrientacaoExif() throws IOException {
        BufferedImage girada = processar(comOrientacaoExif(jpeg(200, 100), 6), FormatoImagem.CAPA);

        assertThat(girada.getWidth()).isEqualTo(100);
        assertThat(girada.getHeight()).isEqualTo(200);
        // O canto vermelho (em cima, à esquerda) vai para cima, à direita.
        assertThat(ehVermelho(girada.getRGB(90, 10))).isTrue();
        assertThat(ehVermelho(girada.getRGB(10, 10))).isFalse();
    }

    @Test
    void orientacao8GiraNoSentidoAntiHorario() throws IOException {
        BufferedImage girada = processar(comOrientacaoExif(jpeg(200, 100), 8), FormatoImagem.CAPA);

        assertThat(girada.getWidth()).isEqualTo(100);
        assertThat(girada.getHeight()).isEqualTo(200);
        // O canto vermelho vai para baixo, à esquerda.
        assertThat(ehVermelho(girada.getRGB(10, 190))).isTrue();
    }

    @Test
    void transparenciaDoPngViraFundoBranco() throws IOException {
        BufferedImage transparente = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage saida = processar(codificar(transparente, "png"), FormatoImagem.CAPA);

        Color pixel = new Color(saida.getRGB(25, 25));
        assertThat(pixel.getRed()).isGreaterThan(240);
        assertThat(pixel.getGreen()).isGreaterThan(240);
        assertThat(pixel.getBlue()).isGreaterThan(240);
    }

    @Test
    void recusaArquivoQueNaoEhImagem() {
        byte[] texto = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processador.processar(texto, FormatoImagem.AVATAR))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessageContaining("não é uma imagem");
    }

    @Test
    void recusaFormatoQueNaoEhJpgNemPng() throws IOException {
        byte[] gif = codificar(desenho(20, 20), "gif");

        assertThatThrownBy(() -> processador.processar(gif, FormatoImagem.AVATAR))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessageContaining("Formato não aceito");
    }

    @Test
    void recusaResolucaoGiganteSemDecodificar() throws IOException {
        byte[] gigante = codificar(new BufferedImage(10_001, 1, BufferedImage.TYPE_INT_RGB), "png");

        assertThatThrownBy(() -> processador.processar(gigante, FormatoImagem.CAPA))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessageContaining("resolução");
    }

    @Test
    void recusaImagemCortadaNoMeio() throws IOException {
        byte[] inteira = png(300, 300);
        byte[] cortada = java.util.Arrays.copyOf(inteira, 60);

        assertThatThrownBy(() -> processador.processar(cortada, FormatoImagem.AVATAR))
                .isInstanceOf(ImagemInvalidaException.class);
    }

    // ------------------------------------------------------------------ apoio

    private BufferedImage processar(byte[] conteudo, FormatoImagem formato) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(processador.processar(conteudo, formato)));
    }

    /** Fundo azul com um quadrado vermelho no canto de cima, à esquerda. */
    private static BufferedImage desenho(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.RED);
        g.fillRect(0, 0, Math.min(40, largura), Math.min(40, altura));
        g.dispose();
        return imagem;
    }

    private static byte[] png(int largura, int altura) throws IOException {
        return codificar(desenho(largura, altura), "png");
    }

    private static byte[] jpeg(int largura, int altura) throws IOException {
        return codificar(desenho(largura, altura), "jpeg");
    }

    private static byte[] codificar(BufferedImage imagem, String formato) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        assertThat(ImageIO.write(imagem, formato, saida)).as("escritor %s", formato).isTrue();
        return saida.toByteArray();
    }

    /** Insere logo depois do SOI um segmento APP1 com EXIF contendo só a tag Orientation. */
    private static byte[] comOrientacaoExif(byte[] jpeg, int orientacao) {
        byte[] app1 = {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,             // APP1, tamanho 34
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,      // TIFF big-endian, IFD0 no offset 8
            0x00, 0x01,                                        // 1 entrada
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,    // Orientation, SHORT, 1 valor
            0x00, (byte) orientacao, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00                             // sem próximo IFD
        };
        byte[] resultado = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, resultado, 0, 2);
        System.arraycopy(app1, 0, resultado, 2, app1.length);
        System.arraycopy(jpeg, 2, resultado, 2 + app1.length, jpeg.length - 2);
        return resultado;
    }

    private static boolean ehVermelho(int rgb) {
        Color cor = new Color(rgb);
        return cor.getRed() > 180 && cor.getGreen() < 90 && cor.getBlue() < 90;
    }
}
