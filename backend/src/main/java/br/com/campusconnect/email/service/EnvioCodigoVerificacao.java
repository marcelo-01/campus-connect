package br.com.campusconnect.email.service;

import br.com.campusconnect.usuario.service.CodigoVerificacaoEmitido;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda o código de verificação para o e-mail da conta.
 *
 * <p>Roda depois do commit e em outra thread. Se a transação de quem gerou o código for desfeita, nenhum e-mail sai.
 * Se o envio falhar, só o log fica sabendo, e a pessoa pode pedir outro código na tela.
 */
@Slf4j
@Component
class EnvioCodigoVerificacao {

    private final JavaMailSender correio;
    private final PropriedadesEmail propriedades;
    private final ModeloEmail modelo = ModeloEmail.ler("verificacao");

    EnvioCodigoVerificacao(JavaMailSender correio, PropriedadesEmail propriedades) {
        this.correio = correio;
        this.propriedades = propriedades;
    }

    @Async
    @TransactionalEventListener
    public void aoEmitir(CodigoVerificacaoEmitido evento) {
        Map<String, String> valores = Map.of(
                "nome", primeiroNome(evento.nomeCompleto()),
                "codigo", evento.codigo(),
                "minutos", String.valueOf(evento.validade().toMinutes()));
        try {
            MimeMessage mensagem = correio.createMimeMessage();
            MimeMessageHelper montagem = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            montagem.setFrom(propriedades.remetente());
            montagem.setTo(evento.email());
            montagem.setSubject(evento.codigo() + " é o seu código do CampusConnect");
            montagem.setText(modelo.texto(valores), modelo.html(valores));
            correio.send(mensagem);
        } catch (MessagingException | MailException falha) {
            log.error("Não foi possível mandar o código de verificação para {}", mascarar(evento.email()), falha);
        }
    }

    static String primeiroNome(String nomeCompleto) {
        return nomeCompleto.strip().split("\\s+", 2)[0];
    }

    /** "ana.beatriz@exemplo.com" vira "a***@exemplo.com": o log não precisa do endereço inteiro. */
    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 0 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
