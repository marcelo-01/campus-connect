package br.com.campusconnect.comum;

/**
 * A pessoa está logada, mas não tem permissão para a ação, como alguém que não é administrador tentando
 * criar uma bolha. A API responde 403 com a mensagem.
 */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
