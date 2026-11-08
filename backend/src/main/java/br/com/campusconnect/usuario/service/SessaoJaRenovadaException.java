package br.com.campusconnect.usuario.service;

/**
 * O refresh token acabou de ser trocado por outra requisição, em geral outra aba do mesmo navegador.
 *
 * <p>A sessão continua valendo e o navegador já recebeu (ou está recebendo) o cookie novo. Por isso a
 * resposta não apaga o cookie: se apagasse, derrubaria a sessão da outra aba. O cliente só precisa tentar
 * de novo.
 */
public class SessaoJaRenovadaException extends SessaoInvalidaException {
}
