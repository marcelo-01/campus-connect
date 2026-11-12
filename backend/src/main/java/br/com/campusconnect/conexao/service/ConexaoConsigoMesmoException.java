package br.com.campusconnect.conexao.service;

/** Uma conexão precisa de duas pessoas diferentes. A API responde 400 com a mensagem. */
public class ConexaoConsigoMesmoException extends RuntimeException {

    public ConexaoConsigoMesmoException() {
        super("Não é possível se conectar com a própria conta.");
    }
}
