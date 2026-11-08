package br.com.campusconnect.comum;

import lombok.Getter;

/**
 * Valor recusado por uma regra que a validação do corpo da requisição não cobre, como um link que não
 * é endereço web ou um arquivo que não é imagem. A API responde 400 apontando o {@link #campo}.
 */
@Getter
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }
}
