package br.com.campusconnect.conversa.api;

import br.com.campusconnect.conversa.service.ConversaConsigoMesmoException;
import br.com.campusconnect.conversa.service.ConversaNaoEncontradaException;
import br.com.campusconnect.conversa.service.LimiteDeEnvioException;
import br.com.campusconnect.conversa.service.MensagemNaoEncontradaException;
import br.com.campusconnect.conversa.service.PessoaIndisponivelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConversaController.class)
class TratadorErrosConversa {

    @ExceptionHandler(ConversaNaoEncontradaException.class)
    ProblemDetail conversaNaoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
    }

    @ExceptionHandler(MensagemNaoEncontradaException.class)
    ProblemDetail mensagemNaoEncontrada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Mensagem não encontrada.");
    }

    @ExceptionHandler(PessoaIndisponivelException.class)
    ProblemDetail pessoaIndisponivel() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Pessoa não encontrada.");
    }

    @ExceptionHandler(ConversaConsigoMesmoException.class)
    ProblemDetail consigoMesmo(ConversaConsigoMesmoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }

    @ExceptionHandler(LimiteDeEnvioException.class)
    ProblemDetail limite(LimiteDeEnvioException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, excecao.getMessage());
    }
}
