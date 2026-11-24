package br.com.campusconnect.usuario.service;

/**
 * A senha da conta acabou de mudar, pela tela de alterar senha ou pelo "Esqueceu a senha?". O módulo {@code email}
 * avisa o dono da conta depois do commit: se não foi ele, o aviso é a chance de reagir.
 *
 * @param nomeCompleto o nome da conta, para a saudação
 */
public record SenhaAlterada(String email, String nomeCompleto) {
}
