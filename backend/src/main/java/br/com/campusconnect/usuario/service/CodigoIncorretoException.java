package br.com.campusconnect.usuario.service;

import br.com.campusconnect.comum.CampoInvalidoException;

/**
 * O código digitado não confere com o que vale agora. A tentativa errada já foi contada e precisa ficar gravada, então
 * quem lança esta exceção não pode desfazer a transação.
 */
public class CodigoIncorretoException extends CampoInvalidoException {

    CodigoIncorretoException(int restantes) {
        super("codigo", mensagem(restantes));
    }

    private static String mensagem(int restantes) {
        if (restantes == 0) {
            return "Código incorreto. Peça um código novo.";
        }
        return restantes == 1
                ? "Código incorreto. Você tem mais 1 tentativa."
                : "Código incorreto. Você tem mais %d tentativas.".formatted(restantes);
    }
}
