-- =====================================================================
-- PUBLICACOES
-- =====================================================================
CREATE TABLE publicacao (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    autor_id       UUID         NOT NULL REFERENCES usuario(id),
    bolha_id       UUID         NOT NULL REFERENCES bolha(id),
    conteudo       TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    fixada         BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em      TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMP,
    removido_em    TIMESTAMP
);

CREATE TABLE comentario (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacao_id      UUID           NOT NULL REFERENCES publicacao(id),
    autor_id           UUID           NOT NULL REFERENCES usuario(id),
    comentario_pai_id  UUID           REFERENCES comentario(id),
    conteudo           VARCHAR(1000)  NOT NULL,
    criado_em          TIMESTAMP      NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMP,
    removido_em        TIMESTAMP,
    CONSTRAINT ck_comentario_pai CHECK (comentario_pai_id IS NULL OR comentario_pai_id <> id)
);

CREATE TABLE midia_publicacao (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacao_id  UUID         NOT NULL REFERENCES publicacao(id) ON DELETE CASCADE,
    tipo           VARCHAR(20)  NOT NULL,
    url            TEXT         NOT NULL,
    mime_type      VARCHAR(80),
    tamanho_bytes  BIGINT,
    ordem          SMALLINT     NOT NULL DEFAULT 0,
    criado_em      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE reacao (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID         NOT NULL REFERENCES usuario(id),
    publicacao_id  UUID         REFERENCES publicacao(id),
    comentario_id  UUID         REFERENCES comentario(id),
    tipo           VARCHAR(20)  NOT NULL,
    criado_em      TIMESTAMP    NOT NULL DEFAULT now(),
    -- a reacao e em uma publicacao OU em um comentario, nunca nos dois
    CONSTRAINT ck_reacao_alvo CHECK (num_nonnulls(publicacao_id, comentario_id) = 1)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_publicacao_autor          ON publicacao (autor_id);
CREATE INDEX ix_publicacao_bolha          ON publicacao (bolha_id, criado_em DESC);
CREATE INDEX ix_comentario_publicacao     ON comentario (publicacao_id);
CREATE INDEX ix_comentario_autor          ON comentario (autor_id);
CREATE INDEX ix_comentario_pai            ON comentario (comentario_pai_id);
CREATE INDEX ix_midia_publicacao          ON midia_publicacao (publicacao_id);
CREATE INDEX ix_reacao_usuario            ON reacao (usuario_id);
CREATE INDEX ix_reacao_publicacao         ON reacao (publicacao_id);
CREATE INDEX ix_reacao_comentario         ON reacao (comentario_id);

-- um usuario reage no maximo uma vez a cada publicacao / comentario
CREATE UNIQUE INDEX uq_reacao_publicacao ON reacao (usuario_id, publicacao_id) WHERE publicacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_reacao_comentario ON reacao (usuario_id, comentario_id) WHERE comentario_id IS NOT NULL;
