package br.com.campusconnect;

import br.com.campusconnect.usuario.service.GeradorCodigoVerificacao;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * O gerador de códigos dos testes. Devolve {@link #PADRAO}, para os testes que só querem uma conta verificada, ou os
 * códigos postos em {@link #proximos}, um por vez, para quem precisa de códigos diferentes.
 */
class CodigosDeTeste extends GeradorCodigoVerificacao {

    static final String PADRAO = "123456";

    private final Queue<String> fila = new ConcurrentLinkedQueue<>();

    void proximos(String... codigos) {
        fila.addAll(List.of(codigos));
    }

    void limpar() {
        fila.clear();
    }

    @Override
    public String gerar() {
        String proximo = fila.poll();
        return proximo != null ? proximo : PADRAO;
    }
}
