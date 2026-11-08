package br.com.campusconnect.usuario.service;

import java.util.List;

/** O que a pessoa pode mudar no próprio perfil. Foto e capa têm métodos próprios em {@link PerfilService}. */
public record AlteracaoPerfil(String nomeCompleto, String username, String bio, String link,
        List<String> habilidades) {
}
