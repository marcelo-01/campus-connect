-- =====================================================================
-- CONVERSAS
-- =====================================================================
CREATE TABLE conversa (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo              VARCHAR(20)   NOT NULL,
    titulo            VARCHAR(120),
    usuario_menor_id  UUID          REFERENCES usuario(id),
    usuario_maior_id  UUID          REFERENCES usuario(id),
    criado_em         TIMESTAMP     NOT NULL DEFAULT now(),
    -- conversa direta: par ordenado preenchido; conversa em grupo: ambos nulos
    CONSTRAINT ck_conversa_par CHECK (
        (usuario_menor_id IS NULL AND usuario_maior_id IS NULL)
        OR (usuario_menor_id IS NOT NULL AND usuario_maior_id IS NOT NULL
            AND usuario_menor_id < usuario_maior_id)
    ),
    CONSTRAINT uq_conversa_par UNIQUE (usuario_menor_id, usuario_maior_id)
);

CREATE TABLE participante_conversa (
    conversa_id        UUID       NOT NULL REFERENCES conversa(id) ON DELETE CASCADE,
    usuario_id         UUID       NOT NULL REFERENCES usuario(id)  ON DELETE CASCADE,
    entrou_em          TIMESTAMP  NOT NULL DEFAULT now(),
    ultima_leitura_em  TIMESTAMP,
    arquivada          BOOLEAN    NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversa_id, usuario_id)
);

CREATE TABLE mensagem (
    id            UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    conversa_id   UUID       NOT NULL REFERENCES conversa(id),
    remetente_id  UUID       NOT NULL REFERENCES usuario(id),
    conteudo      TEXT,
    anexo_url     TEXT,
    enviado_em    TIMESTAMP  NOT NULL DEFAULT now(),
    editado_em    TIMESTAMP,
    removido_em   TIMESTAMP,
    CONSTRAINT ck_mensagem_conteudo CHECK (conteudo IS NOT NULL OR anexo_url IS NOT NULL)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_participante_usuario      ON participante_conversa (usuario_id);
CREATE INDEX ix_mensagem_conversa         ON mensagem (conversa_id, enviado_em);
CREATE INDEX ix_mensagem_remetente        ON mensagem (remetente_id);
