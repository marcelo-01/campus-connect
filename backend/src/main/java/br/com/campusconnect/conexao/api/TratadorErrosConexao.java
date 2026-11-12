package br.com.campusconnect.conexao.api;

import br.com.campusconnect.conexao.service.ConexaoConsigoMesmoException;
import br.com.campusconnect.conexao.service.PessoaNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConexaoController.class)
class TratadorErrosConexao {

    @ExceptionHandler(PessoaNaoEncontradaException.class)
    ProblemDetail naoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Pessoa não encontrada.");
    }

    @ExceptionHandler(ConexaoConsigoMesmoException.class)
    ProblemDetail consigoMesmo(ConexaoConsigoMesmoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }
}
