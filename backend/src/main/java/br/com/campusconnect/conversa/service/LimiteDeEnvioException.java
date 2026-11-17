package br.com.campusconnect.conversa.service;

/** A pessoa passou do limite de mensagens ou de conversas novas. A API responde 429 com a mensagem. */
public class LimiteDeEnvioException extends RuntimeException {

    public LimiteDeEnvioException() {
        super("Você mandou muitas mensagens em pouco tempo. Espere um pouco.");
    }
}
