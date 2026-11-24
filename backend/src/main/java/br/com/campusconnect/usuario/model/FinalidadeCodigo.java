package br.com.campusconnect.usuario.model;

/** Para que serve um código mandado para o e-mail ({@link VerificacaoEmail}). Um não vale no lugar do outro. */
public enum FinalidadeCodigo {

    /** Confirma o e-mail da conta nova, depois do cadastro ou no login de quem ainda não confirmou. */
    VERIFICACAO_EMAIL,

    /** O "Esqueceu a senha?": troca a senha sem saber a atual. */
    REDEFINICAO_SENHA
}
