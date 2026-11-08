package br.com.campusconnect.usuario.api;

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
