package br.com.campusconnect.bolha.api;

import br.com.campusconnect.bolha.service.BolhaComSubBolhasException;
import br.com.campusconnect.bolha.service.BolhaNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BolhaController.class)
class TratadorErrosBolha {

    @ExceptionHandler(BolhaNaoEncontradaException.class)
    ProblemDetail naoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Bolha não encontrada.");
    }

    @ExceptionHandler(BolhaComSubBolhasException.class)
    ProblemDetail comSubBolhas(BolhaComSubBolhasException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excecao.getMessage());
    }
}
