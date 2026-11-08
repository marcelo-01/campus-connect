-- =====================================================================
-- BOLHAS
-- =====================================================================
-- Tipos:
--   INSTITUICAO -> Bolha Geral da instituicao
--   CAMPUS      -> Bolha de Campus
--   CURSO       -> Bolha de Curso
--   COMUNIDADE  -> bolha sem instituicao (interesses, grupos abertos)
-- Qualquer usuario pode entrar em qualquer bolha (membro_bolha nao
-- depende de vinculo). entrada_automatica = TRUE faz com que usuarios
-- com vinculo ativo na instituicao/campus/curso da bolha entrem sozinhos.
CREATE TABLE bolha (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                VARCHAR(20)  NOT NULL,
    nome                VARCHAR(120) NOT NULL,
    slug                VARCHAR(140) NOT NULL UNIQUE,
    descricao           VARCHAR(500),
    bolha_pai_id        UUID         REFERENCES bolha(id),
    instituicao_id      UUID         REFERENCES instituicao(id),
    campus_id           UUID,
    curso_id            UUID,
    entrada_automatica  BOOLEAN      NOT NULL DEFAULT FALSE,
    somente_leitura     BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em           TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMP,
    CONSTRAINT ck_bolha_pai CHECK (bolha_pai_id IS NULL OR bolha_pai_id <> id),
    -- o tipo define quais niveis da hierarquia sao preenchidos
    CONSTRAINT ck_bolha_escopo CHECK (
           (tipo = 'COMUNIDADE'  AND instituicao_id IS NULL     AND campus_id IS NULL     AND curso_id IS NULL)
        OR (tipo = 'INSTITUICAO' AND instituicao_id IS NOT NULL AND campus_id IS NULL     AND curso_id IS NULL)
        OR (tipo = 'CAMPUS'      AND instituicao_id IS NOT NULL AND campus_id IS NOT NULL AND curso_id IS NULL)
        OR (tipo = 'CURSO'       AND instituicao_id IS NOT NULL AND campus_id IS NOT NULL AND curso_id IS NOT NULL)
    ),
    -- bolha de comunidade nao tem para quem fazer entrada automatica
    CONSTRAINT ck_bolha_entrada_automatica CHECK (NOT (tipo = 'COMUNIDADE' AND entrada_automatica)),
    CONSTRAINT fk_bolha_campus FOREIGN KEY (campus_id, instituicao_id)
        REFERENCES campus (id, instituicao_id),
    CONSTRAINT fk_bolha_curso  FOREIGN KEY (curso_id, campus_id)
        REFERENCES curso (id, campus_id)
);

CREATE TABLE membro_bolha (
    bolha_id             UUID        NOT NULL REFERENCES bolha(id)   ON DELETE CASCADE,
    usuario_id           UUID        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    papel                VARCHAR(20) NOT NULL,
    notificacoes_ativas  BOOLEAN     NOT NULL DEFAULT TRUE,
    entrou_em            TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (bolha_id, usuario_id)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_bolha_pai                 ON bolha (bolha_pai_id);
CREATE INDEX ix_bolha_instituicao         ON bolha (instituicao_id);
CREATE INDEX ix_bolha_campus              ON bolha (campus_id);
CREATE INDEX ix_bolha_curso               ON bolha (curso_id);
CREATE INDEX ix_membro_bolha_usuario      ON membro_bolha (usuario_id);
