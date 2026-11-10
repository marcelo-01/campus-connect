package br.com.campusconnect.bolha.service;

/** Excluir uma bolha que ainda tem sub-bolhas apagaria a árvore inteira de uma vez; a exclusão é recusada. */
public class BolhaComSubBolhasException extends RuntimeException {

    public BolhaComSubBolhasException() {
        super("Exclua as sub-bolhas antes de excluir esta bolha.");
    }
}
