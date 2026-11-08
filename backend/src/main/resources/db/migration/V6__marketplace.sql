-- =====================================================================
-- MARKETPLACE
-- =====================================================================
CREATE TABLE categoria_anuncio (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    categoria_pai_id  UUID        REFERENCES categoria_anuncio(id),
    nome              VARCHAR(80) NOT NULL,
    slug              VARCHAR(80) NOT NULL UNIQUE,
    ativa             BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_categoria_pai CHECK (categoria_pai_id IS NULL OR categoria_pai_id <> id)
);

CREATE TABLE anuncio (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    anunciante_id     UUID          NOT NULL REFERENCES usuario(id),
    categoria_id      UUID          NOT NULL REFERENCES categoria_anuncio(id),
    tipo              VARCHAR(20)   NOT NULL,
    titulo            VARCHAR(120)  NOT NULL,
    descricao         TEXT,
    preco             DECIMAL(10,2),
    modalidade_preco  VARCHAR(20),
    status            VARCHAR(20)   NOT NULL,
    publicado_em      TIMESTAMP,
    criado_em         TIMESTAMP     NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMP,
    CONSTRAINT ck_anuncio_preco CHECK (preco IS NULL OR preco >= 0)
);

CREATE TABLE imagem_anuncio (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id  UUID      NOT NULL REFERENCES anuncio(id) ON DELETE CASCADE,
    url         TEXT      NOT NULL,
    ordem       SMALLINT  NOT NULL DEFAULT 0
);

CREATE TABLE avaliacao_anuncio (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id  UUID          NOT NULL REFERENCES anuncio(id),
    autor_id    UUID          NOT NULL REFERENCES usuario(id),
    nota        SMALLINT      NOT NULL,
    comentario  VARCHAR(500),
    criado_em   TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_avaliacao_nota CHECK (nota BETWEEN 1 AND 5),
    CONSTRAINT uq_avaliacao_autor UNIQUE (anuncio_id, autor_id)
);

CREATE TABLE favorito_anuncio (
    usuario_id  UUID       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    anuncio_id  UUID       NOT NULL REFERENCES anuncio(id) ON DELETE CASCADE,
    criado_em   TIMESTAMP  NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, anuncio_id)
);

CREATE TABLE anuncio_bolha (
    anuncio_id    UUID       NOT NULL REFERENCES anuncio(id) ON DELETE CASCADE,
    bolha_id      UUID       NOT NULL REFERENCES bolha(id)   ON DELETE CASCADE,
    vinculado_em  TIMESTAMP  NOT NULL DEFAULT now(),
    PRIMARY KEY (anuncio_id, bolha_id)
);

CREATE TABLE impulsionamento (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    anuncio_id      UUID           NOT NULL REFERENCES anuncio(id),
    bolha_id        UUID           NOT NULL REFERENCES bolha(id),
    periodo_inicio  TIMESTAMP      NOT NULL,
    periodo_fim     TIMESTAMP      NOT NULL,
    valor_pago      DECIMAL(10,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    criado_em       TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT ck_impulsionamento_periodo CHECK (periodo_fim > periodo_inicio),
    CONSTRAINT ck_impulsionamento_valor   CHECK (valor_pago >= 0)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_categoria_pai             ON categoria_anuncio (categoria_pai_id);
CREATE INDEX ix_anuncio_anunciante        ON anuncio (anunciante_id);
CREATE INDEX ix_anuncio_categoria         ON anuncio (categoria_id);
CREATE INDEX ix_imagem_anuncio            ON imagem_anuncio (anuncio_id);
CREATE INDEX ix_avaliacao_autor           ON avaliacao_anuncio (autor_id);
CREATE INDEX ix_favorito_anuncio          ON favorito_anuncio (anuncio_id);
CREATE INDEX ix_anuncio_bolha_bolha       ON anuncio_bolha (bolha_id);
CREATE INDEX ix_impulsionamento_anuncio   ON impulsionamento (anuncio_id);
CREATE INDEX ix_impulsionamento_bolha     ON impulsionamento (bolha_id);
