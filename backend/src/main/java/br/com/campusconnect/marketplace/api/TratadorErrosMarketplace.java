package br.com.campusconnect.marketplace.api;

import br.com.campusconnect.marketplace.service.AnuncioNaoEncontradoException;
import br.com.campusconnect.marketplace.service.AvaliacaoNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AnuncioController.class)
class TratadorErrosMarketplace {

    @ExceptionHandler(AnuncioNaoEncontradoException.class)
    ProblemDetail anuncioNaoEncontrado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Anúncio não encontrado.");
    }

    @ExceptionHandler(AvaliacaoNaoEncontradaException.class)
    ProblemDetail avaliacaoNaoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Avaliação não encontrada.");
    }
}
