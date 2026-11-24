package br.com.campusconnect;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Um {@code JavaMailSender} que guarda os e-mails em vez de mandá-los. Assim, nenhum teste sai para a rede. Como o
 * envio roda em outra thread depois do commit, quem confere um e-mail precisa esperar por ele com {@link #esperar}.
 */
class CaixaDeSaidaFalsa extends JavaMailSenderImpl {

    private final List<MimeMessage> enviadas = new CopyOnWriteArrayList<>();

    /** O {@code saveChanges} é o que o envio de verdade faz antes de mandar: ele monta os cabeçalhos de cada parte. */
    @Override
    protected void doSend(MimeMessage[] mensagens, Object[] originais) {
        for (MimeMessage mensagem : mensagens) {
            try {
                mensagem.saveChanges();
            } catch (MessagingException excecao) {
                throw new IllegalStateException(excecao);
            }
            enviadas.add(mensagem);
        }
    }

    void esvaziar() {
        enviadas.clear();
    }

    List<MimeMessage> para(String email) {
        return enviadas.stream().filter(mensagem -> ehPara(mensagem, email)).toList();
    }

    /** Espera até {@code quantos} e-mails para o endereço, por no máximo 5 s, e devolve o último. */
    MimeMessage esperar(String email, int quantos) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(limite)) {
            List<MimeMessage> recebidas = para(email);
            if (recebidas.size() >= quantos) {
                return recebidas.getLast();
            }
            Thread.sleep(20);
        }
        throw new AssertionError(
                "Esperava " + quantos + " e-mail(s) para " + email + ", chegaram " + para(email).size());
    }

    /** O corpo do tipo pedido ({@code text/html} ou {@code text/plain}), já decodificado. */
    static String corpo(MimeMessage mensagem, String tipo) throws IOException, MessagingException {
        return procurar(mensagem, tipo)
                .orElseThrow(() -> new AssertionError("O e-mail não tem a parte " + tipo));
    }

    private static Optional<String> procurar(Part parte, String tipo) throws IOException, MessagingException {
        if (parte.isMimeType(tipo)) {
            return Optional.of((String) parte.getContent());
        }
        if (parte.getContent() instanceof Multipart partes) {
            for (int i = 0; i < partes.getCount(); i++) {
                Optional<String> achado = procurar(partes.getBodyPart(i), tipo);
                if (achado.isPresent()) {
                    return achado;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean ehPara(MimeMessage mensagem, String email) {
        try {
            Address[] destinatarios = Optional.ofNullable(mensagem.getAllRecipients()).orElse(new Address[0]);
            return Arrays.stream(destinatarios).anyMatch(destino -> destino.toString().equalsIgnoreCase(email));
        } catch (MessagingException excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
