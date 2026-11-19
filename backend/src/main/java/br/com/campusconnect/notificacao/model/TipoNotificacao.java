package br.com.campusconnect.notificacao.model;

/**
 * O que aconteceu. Mensagens não viram notificação, porque já têm o contador delas no menu, e posts novos nas bolhas
 * também não, porque o feed já avisa.
 */
public enum TipoNotificacao {
    /** Alguém pediu para se conectar. */
    SOLICITACAO_CONEXAO(Repeticao.RENOVA, false),
    /** Alguém aceitou o pedido de conexão. */
    CONEXAO_ACEITA(Repeticao.RENOVA, false),
    /** Alguém comentou na publicação. */
    NOVO_COMENTARIO(Repeticao.SEMPRE, false),
    /** Alguém respondeu ao comentário. */
    RESPOSTA_COMENTARIO(Repeticao.SEMPRE, false),
    CURTIDA_PUBLICACAO(Repeticao.UMA_VEZ, true),
    CURTIDA_COMENTARIO(Repeticao.UMA_VEZ, true),
    REPUBLICACAO(Repeticao.UMA_VEZ, true),
    AVALIACAO_ANUNCIO(Repeticao.UMA_VEZ, false),
    /** Reservado para quando as denúncias forem analisadas. */
    MODERACAO(Repeticao.SEMPRE, false);

    /**
     * O que acontece quando a mesma pessoa faz a mesma coisa no mesmo alvo de novo. Os tipos que não são
     * {@link #SEMPRE} estão no índice único {@code ux_notificacao_uma_por_pessoa} da migration V21.
     */
    public enum Repeticao {
        /** Cada vez é uma notificação: cada comentário é outro. */
        SEMPRE,
        /** Só a primeira avisa: curtir, descurtir e curtir de novo não avisa duas vezes. */
        UMA_VEZ,
        /** A notificação volta ao topo, como nova: pedir conexão de novo depois de desfazer a anterior. */
        RENOVA
    }

    private final Repeticao repeticao;
    private final boolean agrupa;

    TipoNotificacao(Repeticao repeticao, boolean agrupa) {
        this.repeticao = repeticao;
        this.agrupa = agrupa;
    }

    public Repeticao repeticao() {
        return repeticao;
    }

    /** Se as notificações do mesmo alvo aparecem juntas na tela: "Ana e mais 3 pessoas curtiram". */
    public boolean agrupa() {
        return agrupa;
    }
}
