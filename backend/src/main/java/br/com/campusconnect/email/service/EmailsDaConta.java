package br.com.campusconnect.email.service;

import br.com.campusconnect.usuario.service.CodigoRedefinicaoSenhaEmitido;
import br.com.campusconnect.usuario.service.CodigoVerificacaoEmitido;
import br.com.campusconnect.usuario.service.SenhaAlterada;
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
 * Os e-mails sobre a conta: o código que verifica o e-mail, o código que redefine a senha e o aviso de senha
 * alterada.
 *
 * <p>Cada um sai depois do commit e em outra thread. Se a transação de quem gerou o evento for desfeita, nenhum e-mail
 * sai. Se o envio falhar, só o log fica sabendo: os códigos podem ser pedidos de novo na tela.
 */
@Slf4j
@Component
class EmailsDaConta {

    private final JavaMailSender correio;
    private final PropriedadesEmail propriedades;
    private final ModeloEmail verificacao = ModeloEmail.ler("verificacao");
    private final ModeloEmail redefinicao = ModeloEmail.ler("redefinicao-senha");
    private final ModeloEmail senhaAlterada = ModeloEmail.ler("senha-alterada");

    EmailsDaConta(JavaMailSender correio, PropriedadesEmail propriedades) {
        this.correio = correio;
        this.propriedades = propriedades;
    }

    @Async
    @TransactionalEventListener
    public void aoEmitirCodigoDeVerificacao(CodigoVerificacaoEmitido evento) {
        mandar(evento.email(), evento.codigo() + " é o seu código do CampusConnect", verificacao, Map.of(
                "nome", primeiroNome(evento.nomeCompleto()),
                "codigo", evento.codigo(),
                "minutos", String.valueOf(evento.validade().toMinutes())), "o código de verificação");
    }

    @Async
    @TransactionalEventListener
    public void aoEmitirCodigoDeRedefinicao(CodigoRedefinicaoSenhaEmitido evento) {
        mandar(evento.email(), evento.codigo() + " é o seu código para redefinir a senha", redefinicao, Map.of(
                "nome", primeiroNome(evento.nomeCompleto()),
                "codigo", evento.codigo(),
                "minutos", String.valueOf(evento.validade().toMinutes())), "o código de redefinição de senha");
    }

    @Async
    @TransactionalEventListener
    public void aoAlterarSenha(SenhaAlterada evento) {
        mandar(evento.email(), "A sua senha do CampusConnect foi alterada", senhaAlterada,
                Map.of("nome", primeiroNome(evento.nomeCompleto())), "o aviso de senha alterada");
    }

    /** @param assuntoDoLog o que este e-mail é, para a mensagem de falha */
    private void mandar(String para, String assunto, ModeloEmail modelo, Map<String, String> valores,
            String assuntoDoLog) {
        try {
            MimeMessage mensagem = correio.createMimeMessage();
            MimeMessageHelper montagem = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            montagem.setFrom(propriedades.remetente());
            montagem.setTo(para);
            montagem.setSubject(assunto);
            montagem.setText(modelo.texto(valores), modelo.html(valores));
            correio.send(mensagem);
        } catch (MessagingException | MailException falha) {
            log.error("Não foi possível mandar {} para {}", assuntoDoLog, mascarar(para), falha);
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
