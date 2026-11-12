package br.com.campusconnect;

import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/**
 * Um servidor HTTP de mentira em {@code 127.0.0.1}, numa porta livre, para os testes da prévia de links.
 * Cada caminho responde o que o teste mandar.
 */
public final class ServidorPaginas implements AutoCloseable {

    private final HttpServer servidor;
    private final ExecutorService tarefas = Executors.newCachedThreadPool();

    public ServidorPaginas() {
        try {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
        servidor.setExecutor(tarefas);
        servidor.start();
    }

    public String url(String caminho) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + caminho;
    }

    public ServidorPaginas html(String caminho, String html) {
        return responder(caminho, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    public ServidorPaginas responder(String caminho, int status, String tipo, byte[] corpo) {
        servidor.createContext(caminho, troca -> {
            troca.getResponseHeaders().set("Content-Type", tipo);
            troca.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        return this;
    }

    public ServidorPaginas redirecionar(String caminho, String destino) {
        servidor.createContext(caminho, troca -> {
            troca.getResponseHeaders().set("Location", destino);
            troca.sendResponseHeaders(302, -1);
            troca.close();
        });
        return this;
    }

    /** Responde só depois de {@code espera}, para testar o tempo-limite. */
    public ServidorPaginas lento(String caminho, Duration espera, String html) {
        byte[] corpo = html.getBytes(StandardCharsets.UTF_8);
        servidor.createContext(caminho, troca -> {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
            troca.getResponseHeaders().set("Content-Type", "text/html");
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            } catch (IOException clienteDesistiu) {
                // O cliente já desistiu por tempo; é o esperado.
            }
        });
        return this;
    }

    @Override
    public void close() {
        servidor.stop(0);
        tarefas.shutdownNow();
    }

    /** Um PNG de verdade, de uma cor só. */
    public static byte[] png(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagem, "png", saida);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
        return saida.toByteArray();
    }
}
