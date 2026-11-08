package br.com.campusconnect.comum.web;

import br.com.campusconnect.comum.CampoDuplicadoException;
import br.com.campusconnect.comum.CampoInvalidoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros de toda a API no formato {@link ProblemDetail} (RFC 9457).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler}, então os erros do próprio Spring MVC (404, 405,
 * 415...) também saem como ProblemDetail. Erros de campo vão em {@code erros}, um mapa de campo para
 * mensagem, que o frontend coloca embaixo de cada input.
 */
@RestControllerAdvice
class TratadorErrosApi extends ResponseEntityExceptionHandler {

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excecao,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError erro : excecao.getBindingResult().getFieldErrors()) {
            // "habilidades[2]" vira "habilidades": o formulário mostra o erro embaixo da lista inteira.
            erros.putIfAbsent(erro.getField().replaceFirst("\\[\\d+]$", ""), erro.getDefaultMessage());
        }
        ProblemDetail problema = comErros(HttpStatus.BAD_REQUEST, "Confira os campos destacados.", erros);
        return handleExceptionInternal(excecao, problema, headers, status, requisicao);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException excecao,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, "O corpo da requisição não é um JSON válido.");
        return handleExceptionInternal(excecao, problema, headers, status, requisicao);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException excecao, HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        ProblemDetail problema = comErros(HttpStatus.CONTENT_TOO_LARGE, "O arquivo é grande demais.",
                Map.of("arquivo", "Envie uma imagem de até 5 MB."));
        return handleExceptionInternal(excecao, problema, headers, HttpStatus.CONTENT_TOO_LARGE, requisicao);
    }

    @ExceptionHandler(CampoDuplicadoException.class)
    ProblemDetail campoDuplicado(CampoDuplicadoException excecao) {
        return comErros(HttpStatus.CONFLICT, excecao.getMessage(), Map.of(excecao.getCampo(), excecao.getMessage()));
    }

    @ExceptionHandler(CampoInvalidoException.class)
    ProblemDetail campoInvalido(CampoInvalidoException excecao) {
        return comErros(HttpStatus.BAD_REQUEST, excecao.getMessage(), Map.of(excecao.getCampo(), excecao.getMessage()));
    }

    private static ProblemDetail comErros(HttpStatus status, String detalhe, Map<String, String> erros) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setProperty("erros", erros);
        return problema;
    }
}
