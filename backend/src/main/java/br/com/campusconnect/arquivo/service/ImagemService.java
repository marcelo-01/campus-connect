package br.com.campusconnect.arquivo.service;

import br.com.campusconnect.arquivo.config.PropriedadesArquivos;
import br.com.campusconnect.arquivo.processamento.ProcessadorImagem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Guarda imagens em disco e devolve o caminho público delas ({@code /api/arquivos/<nome>.jpg}).
 *
 * <p>Os outros módulos gravam esse caminho no banco. O nome do arquivo é aleatório, então o conteúdo de
 * um caminho nunca muda: dá para o navegador guardar em cache para sempre.
 */
@Slf4j
@Service
public class ImagemService {

    public static final String PREFIXO_URL = "/api/arquivos/";

    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f]{32}\\.jpg");

    private final ProcessadorImagem processador;
    private final Path diretorio;

    public ImagemService(ProcessadorImagem processador, PropriedadesArquivos propriedades) {
        this.processador = processador;
        this.diretorio = propriedades.diretorio().toAbsolutePath().normalize();
        try {
            Files.createDirectories(diretorio);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível criar a pasta de arquivos " + diretorio, erro);
        }
    }

    /**
     * Valida, ajusta e grava a imagem.
     *
     * @return caminho público da imagem gravada
     * @throws ImagemInvalidaException se o conteúdo não for um JPG ou PNG aceitável
     */
    public String salvar(byte[] conteudo, FormatoImagem formato) {
        byte[] jpeg = processador.processar(conteudo, formato);
        String nome = UUID.randomUUID().toString().replace("-", "") + ".jpg";
        try {
            Files.write(diretorio.resolve(nome), jpeg, StandardOpenOption.CREATE_NEW);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível gravar a imagem " + nome, erro);
        }
        return PREFIXO_URL + nome;
    }

    /** Apaga a imagem de um caminho devolvido por {@link #salvar}. Caminho nulo ou desconhecido é ignorado. */
    public void remover(String url) {
        if (url == null || !url.startsWith(PREFIXO_URL)) {
            return;
        }
        localizar(url.substring(PREFIXO_URL.length())).ifPresent(arquivo -> {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException erro) {
                log.warn("Não foi possível apagar a imagem {}", arquivo, erro);
            }
        });
    }

    /**
     * Arquivo em disco de um nome publicado. Só aceita nomes no formato gerado aqui, então um nome como
     * {@code ../application.yml} nunca sai da pasta de arquivos.
     */
    public Optional<Path> localizar(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            return Optional.empty();
        }
        Path arquivo = diretorio.resolve(nome);
        return Files.isRegularFile(arquivo) ? Optional.of(arquivo) : Optional.empty();
    }
}
