package br.com.campusconnect.comum;

import lombok.Getter;

/**
 * Valor que precisa ser único e já está em uso, como e-mail ou username. A API responde 409
 * apontando o {@link #campo}, para o formulário mostrar o erro no lugar certo.
 */
@Getter
public class CampoDuplicadoException extends RuntimeException {

    private final String campo;

    public CampoDuplicadoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }
}
