package br.com.campusconnect.arquivo.service;

import br.com.campusconnect.comum.CampoInvalidoException;

/** O arquivo enviado não é um vídeo aceito. A API responde 400 no campo {@code arquivo}. */
public class VideoInvalidoException extends CampoInvalidoException {

    public VideoInvalidoException(String mensagem) {
        super("arquivo", mensagem);
    }
}
