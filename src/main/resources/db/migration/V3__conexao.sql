-- =====================================================================
-- CONEXOES
-- =====================================================================
CREATE TABLE conexao (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_menor_id  UUID        NOT NULL REFERENCES usuario(id),
    usuario_maior_id  UUID        NOT NULL REFERENCES usuario(id),
    solicitante_id    UUID        NOT NULL REFERENCES usuario(id),
    status            VARCHAR(20) NOT NULL,
    solicitado_em     TIMESTAMP   NOT NULL DEFAULT now(),
    respondido_em     TIMESTAMP,
    -- par ordenado: impede (A,B) e (B,A) duplicados e auto-conexao
    CONSTRAINT ck_conexao_par_ordenado CHECK (usuario_menor_id < usuario_maior_id),
    CONSTRAINT ck_conexao_solicitante  CHECK (solicitante_id IN (usuario_menor_id, usuario_maior_id)),
    CONSTRAINT uq_conexao_par UNIQUE (usuario_menor_id, usuario_maior_id)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_conexao_maior             ON conexao (usuario_maior_id);
