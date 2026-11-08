package br.com.campusconnect.arquivo.service;

import br.com.campusconnect.comum.CampoInvalidoException;

/** O arquivo enviado não é uma imagem aceita. A API responde 400 no campo {@code arquivo}. */
public class ImagemInvalidaException extends CampoInvalidoException {

    public ImagemInvalidaException(String mensagem) {
        super("arquivo", mensagem);
    }
}
