package br.com.campusconnect.publicacao.api;

import br.com.campusconnect.bolha.service.BolhaNaoEncontradaException;
import br.com.campusconnect.publicacao.service.ComentarioNaoEncontradoException;
import br.com.campusconnect.publicacao.service.PublicacaoNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PublicacaoController.class)
class TratadorErrosPublicacao {

    @ExceptionHandler(PublicacaoNaoEncontradaException.class)
    ProblemDetail publicacaoNaoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Publicação não encontrada.");
    }

    @ExceptionHandler(ComentarioNaoEncontradoException.class)
    ProblemDetail comentarioNaoEncontrado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Comentário não encontrado.");
    }

    @ExceptionHandler(BolhaNaoEncontradaException.class)
    ProblemDetail bolhaNaoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Bolha não encontrada.");
    }
}
