package br.com.campusconnect.usuario.service;

import java.time.Duration;
import lombok.Getter;

/** A conta pediu códigos demais em pouco tempo. A API responde 429 com o tempo de espera. */
@Getter
public class LimiteDeCodigosException extends RuntimeException {

    /** Quanto falta para a conta poder receber outro código, arredondado para cima. */
    private final long segundos;

    LimiteDeCodigosException(Duration espera) {
        super(mensagem(espera));
        this.segundos = segundosParaCima(espera);
    }

    private static String mensagem(Duration espera) {
        long segundos = segundosParaCima(espera);
        if (segundos <= 60) {
            return "Espere %d segundos para pedir outro código.".formatted(segundos);
        }
        long minutos = (segundos + 59) / 60;
        return "Você pediu muitos códigos. Tente de novo em %d minutos.".formatted(minutos);
    }

    private static long segundosParaCima(Duration espera) {
        return Math.max(1, (espera.toMillis() + 999) / 1000);
    }
}
