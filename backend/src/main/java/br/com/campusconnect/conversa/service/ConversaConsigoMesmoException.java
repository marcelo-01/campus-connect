package br.com.campusconnect.conversa.service;

/** Uma conversa precisa de duas pessoas diferentes. A API responde 400 com a mensagem. */
public class ConversaConsigoMesmoException extends RuntimeException {

    public ConversaConsigoMesmoException() {
        super("Não é possível conversar com a própria conta.");
    }
}
