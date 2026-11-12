package br.com.campusconnect.arquivo.service;

/** Como cada tipo de imagem é ajustado antes de ser gravado. */
public enum FormatoImagem {

    /** Foto de perfil: recortada no centro em quadrado, com no máximo 512 px de lado. */
    AVATAR(512, true),

    /** Capa do perfil: mantém a proporção, com no máximo 1600 px no maior lado. */
    CAPA(1600, false),

    /** Imagem de uma publicação: mantém a proporção, com no máximo 1600 px no maior lado. */
    PUBLICACAO(1600, false),

    /** Imagem de destaque da prévia de um link: mantém a proporção, com no máximo 800 px no maior lado. */
    PREVIA_LINK(800, false);

    private final int ladoMaximo;
    private final boolean quadrado;

    FormatoImagem(int ladoMaximo, boolean quadrado) {
        this.ladoMaximo = ladoMaximo;
        this.quadrado = quadrado;
    }

    public int ladoMaximo() {
        return ladoMaximo;
    }

    public boolean quadrado() {
        return quadrado;
    }
}
