package br.com.campusconnect.usuario.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Sorteia o código de 6 dígitos mandado para o e-mail. É um bean à parte para os testes saberem o código. */
@Component
public class GeradorCodigoVerificacao {

    private final SecureRandom aleatorio = new SecureRandom();

    /** De "000000" a "999999", com o mesmo peso para todos. */
    public String gerar() {
        return "%06d".formatted(aleatorio.nextInt(1_000_000));
    }
}
