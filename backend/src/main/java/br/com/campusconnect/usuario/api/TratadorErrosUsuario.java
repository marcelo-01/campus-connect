package br.com.campusconnect.usuario.api;

import br.com.campusconnect.usuario.service.UsuarioNaoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UsuarioController.class)
class TratadorErrosUsuario {

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    ProblemDetail naoEncontrado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Conta não encontrada.");
    }
}
