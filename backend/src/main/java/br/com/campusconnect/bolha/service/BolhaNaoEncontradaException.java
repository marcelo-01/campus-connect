package br.com.campusconnect.bolha.service;

import java.util.UUID;

public class BolhaNaoEncontradaException extends RuntimeException {

    public BolhaNaoEncontradaException(UUID id) {
        super("Bolha não encontrada: " + id);
    }
}
