package br.com.campusconnect.usuario.service;

/** O cadastro deu certo, e um código foi mandado para o e-mail. A sessão só abre depois que o código confere. */
public record CadastroPendente(String email) {
}
