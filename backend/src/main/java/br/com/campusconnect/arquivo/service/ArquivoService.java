package br.com.campusconnect.arquivo.service;

import br.com.campusconnect.arquivo.config.PropriedadesArquivos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A pasta dos arquivos enviados. Grava cada arquivo com um nome aleatório, localiza pelo nome e apaga.
 * As imagens passam antes pelo {@link ImagemService} e os vídeos pelo {@link VideoService}. Os outros
 * módulos guardam no banco só o caminho público ({@code /api/arquivos/<nome>}).
 *
 * <p>O conteúdo de um caminho nunca muda, porque trocar um arquivo gera outro nome. Assim o navegador
 * pode guardar em cache para sempre.
 */
@Slf4j
@Service
public class ArquivoService {

    public static final String PREFIXO_URL = "/api/arquivos/";

    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f]{32}\\.(jpg|mp4|webm)");

    private final Path diretorio;

    public ArquivoService(PropriedadesArquivos propriedades) {
        this.diretorio = propriedades.diretorio().toAbsolutePath().normalize();
        try {
            Files.createDirectories(diretorio);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível criar a pasta de arquivos " + diretorio, erro);
        }
    }

    /** @return caminho público do arquivo gravado */
    String gravar(byte[] conteudo, String extensao) {
        String nome = novoNome(extensao);
        try {
            Files.write(diretorio.resolve(nome), conteudo, StandardOpenOption.CREATE_NEW);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível gravar o arquivo " + nome, erro);
        }
        return PREFIXO_URL + nome;
    }

    /**
     * Copia o fluxo para um arquivo novo sem carregar tudo na memória. Se passar de {@code limite} bytes,
     * apaga o que já gravou e lança a exceção de {@code grandeDemais}.
     *
     * @return caminho público do arquivo gravado
     */
    String gravar(InputStream conteudo, String extensao, long limite, Supplier<RuntimeException> grandeDemais) {
        String nome = novoNome(extensao);
        Path arquivo = diretorio.resolve(nome);
        try (OutputStream saida = Files.newOutputStream(arquivo, StandardOpenOption.CREATE_NEW)) {
            byte[] bloco = new byte[64 * 1024];
            long total = 0;
            for (int lidos; (lidos = conteudo.read(bloco)) != -1; ) {
                total += lidos;
                if (total > limite) {
                    throw grandeDemais.get();
                }
                saida.write(bloco, 0, lidos);
            }
        } catch (IOException erro) {
            apagar(arquivo);
            throw new UncheckedIOException("Não foi possível gravar o arquivo " + nome, erro);
        } catch (RuntimeException erro) {
            apagar(arquivo);
            throw erro;
        }
        return PREFIXO_URL + nome;
    }

    /** Apaga o arquivo de um caminho público. Caminho nulo ou desconhecido é ignorado. */
    public void remover(@Nullable String url) {
        if (url == null || !url.startsWith(PREFIXO_URL)) {
            return;
        }
        localizar(url.substring(PREFIXO_URL.length())).ifPresent(ArquivoService::apagar);
    }

    /**
     * Agenda a limpeza para quando a transação atual terminar: se ela confirmar, apaga
     * {@code seConfirmar}; se desfizer, apaga {@code seDesfazer}. Quem troca um arquivo grava o novo antes
     * de mexer no banco e chama isto com o antigo e o novo; quem exclui chama com o atual e {@code null}.
     * Assim nenhum arquivo fica órfão na pasta e nenhum registro aponta para arquivo apagado.
     *
     * @throws IllegalStateException fora de uma transação
     */
    public void descartarAoFim(@Nullable String seConfirmar, @Nullable String seDesfazer) {
        descartarAoFim(seConfirmar == null ? List.of() : List.of(seConfirmar),
                seDesfazer == null ? List.of() : List.of(seDesfazer));
    }

    /** O mesmo que {@link #descartarAoFim(String, String)}, para vários arquivos de uma vez. */
    public void descartarAoFim(Collection<String> seConfirmar, Collection<String> seDesfazer) {
        List<String> confirmar = List.copyOf(seConfirmar);
        List<String> desfazer = List.copyOf(seDesfazer);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                (status == STATUS_COMMITTED ? confirmar : desfazer).forEach(ArquivoService.this::remover);
            }
        });
    }

    /**
     * Arquivo em disco de um nome publicado. Só aceita nomes no formato gerado aqui, então um nome como
     * {@code ../application.yml} nunca sai da pasta de arquivos.
     */
    public Optional<Path> localizar(@Nullable String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            return Optional.empty();
        }
        Path arquivo = diretorio.resolve(nome);
        return Files.isRegularFile(arquivo) ? Optional.of(arquivo) : Optional.empty();
    }

    private static String novoNome(String extensao) {
        return UUID.randomUUID().toString().replace("-", "") + "." + extensao;
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException erro) {
            log.warn("Não foi possível apagar o arquivo {}", arquivo, erro);
        }
    }
}
