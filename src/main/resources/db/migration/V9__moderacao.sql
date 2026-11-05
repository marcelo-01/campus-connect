-- =====================================================================
-- MODERACAO
-- =====================================================================
CREATE TABLE denuncia (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    denunciante_id   UUID          NOT NULL REFERENCES usuario(id),
    publicacao_id    UUID          REFERENCES publicacao(id),
    comentario_id    UUID          REFERENCES comentario(id),
    anuncio_id       UUID          REFERENCES anuncio(id),
    usuario_alvo_id  UUID          REFERENCES usuario(id),
    motivo           VARCHAR(30)   NOT NULL,
    descricao        VARCHAR(500),
    status           VARCHAR(20)   NOT NULL,
    moderador_id     UUID          REFERENCES usuario(id),
    resolvido_em     TIMESTAMP,
    criado_em        TIMESTAMP     NOT NULL DEFAULT now(),
    -- toda denuncia aponta para exatamente um alvo
    CONSTRAINT ck_denuncia_alvo CHECK (
        num_nonnulls(publicacao_id, comentario_id, anuncio_id, usuario_alvo_id) = 1
    )
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_denuncia_denunciante      ON denuncia (denunciante_id);
CREATE INDEX ix_denuncia_status           ON denuncia (status);
