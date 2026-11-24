package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.EmailJaVerificadoException;
import br.com.campusconnect.usuario.service.EmailNaoVerificadoException;
import br.com.campusconnect.usuario.service.LimiteDeCodigosException;
import br.com.campusconnect.usuario.service.SessaoInvalidaException;
import br.com.campusconnect.usuario.service.SessaoJaRenovadaException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AutenticacaoController.class)
@RequiredArgsConstructor
class TratadorErrosAutenticacao {

    /** O valor de {@code codigo} no 403 do login de quem ainda não verificou o e-mail. */
    static final String EMAIL_NAO_VERIFICADO = "email-nao-verificado";

    private final CookieRefreshToken cookie;

    /** E-mail inexistente e senha errada têm a mesma resposta, para não revelar quem tem conta. */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail credenciaisInvalidas() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
    }

    /** Só chega aqui quem acertou a senha (ver {@code SegurancaConfig#gerenciadorAutenticacao}). */
    @ExceptionHandler(AccountStatusException.class)
    ProblemDetail contaInativa() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Esta conta não está ativa.");
    }

    /**
     * Também só chega aqui quem acertou a senha. O {@code codigo} diz ao frontend que ele deve levar a pessoa para a
     * tela do código. O Spring escolhe este handler, e não o de {@link AccountStatusException}, porque ele é o mais
     * específico.
     */
    @ExceptionHandler(EmailNaoVerificadoException.class)
    ProblemDetail emailNaoVerificado(EmailNaoVerificadoException excecao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, excecao.getMessage());
        problema.setProperty("codigo", EMAIL_NAO_VERIFICADO);
        return problema;
    }

    @ExceptionHandler(EmailJaVerificadoException.class)
    ProblemDetail emailJaVerificado(EmailJaVerificadoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(LimiteDeCodigosException.class)
    ResponseEntity<ProblemDetail> limiteDeCodigos(LimiteDeCodigosException excecao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, excecao.getMessage());
        problema.setProperty("aguardarSegundos", excecao.getSegundos());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(excecao.getSegundos()))
                .body(problema);
    }

    /** Sem {@code Set-Cookie}: apagar o cookie aqui derrubaria a sessão da aba que acabou de renová-lo. */
    @ExceptionHandler(SessaoJaRenovadaException.class)
    ProblemDetail sessaoJaRenovada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A sessão acabou de ser renovada em outra aba. Tente de novo.");
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    ResponseEntity<ProblemDetail> sessaoInvalida(SessaoInvalidaException excecao) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, excecao.getMessage()));
    }
}
