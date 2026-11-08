package br.com.campusconnect.usuario.service;

/** Dados para criar uma conta. A senha chega pura e só sai daqui como hash Argon2. */
public record NovoUsuario(String nomeCompleto, String username, String email, String senha) {

    @Override
    public String toString() {
        return "NovoUsuario[username=" + username + ", email=" + email + "]";
    }
}
