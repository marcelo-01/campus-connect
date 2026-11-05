-- =====================================================================
-- USUARIO
-- =====================================================================
-- Conta independente de instituicao: so dados da pessoa.
CREATE TABLE usuario (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username          VARCHAR(30)  NOT NULL UNIQUE,
    nome_completo     VARCHAR(150) NOT NULL,
    nome_exibicao     VARCHAR(60),
    email             VARCHAR(150) NOT NULL UNIQUE,
    senha_hash        VARCHAR(100) NOT NULL,
    bio               VARCHAR(500),
    foto_perfil_url   TEXT,
    email_verificado  BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL,
    ultimo_acesso_em  TIMESTAMP,
    criado_em         TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMP,
    anonimizado_em    TIMESTAMP
);

-- Vinculo opcional (0..N) do usuario com uma instituicao.
-- Usado para identificar o usuario como aluno/professor/etc. e para a
-- entrada automatica nas bolhas institucionais. NAO e requisito para
-- usar a rede nem para entrar em bolhas.
CREATE TABLE vinculo_institucional (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    instituicao_id  UUID         NOT NULL REFERENCES instituicao(id),
    campus_id       UUID,
    curso_id        UUID,
    tipo            VARCHAR(20)  NOT NULL,  -- ALUNO, EGRESSO, PROFESSOR, SERVIDOR
    matricula       VARCHAR(30),
    verificado_em   TIMESTAMP,              -- NULL = vinculo ainda nao comprovado
    encerrado_em    TIMESTAMP,              -- NULL = vinculo ativo
    criado_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMP,
    -- curso exige campus (senao a FK composta do curso nao e verificada)
    CONSTRAINT ck_vinculo_curso_campus CHECK (curso_id IS NULL OR campus_id IS NOT NULL),
    CONSTRAINT fk_vinculo_campus FOREIGN KEY (campus_id, instituicao_id)
        REFERENCES campus (id, instituicao_id),
    CONSTRAINT fk_vinculo_curso  FOREIGN KEY (curso_id, campus_id)
        REFERENCES curso (id, campus_id),
    -- a mesma matricula nao pode ser usada por duas contas na mesma instituicao
    CONSTRAINT uq_vinculo_matricula UNIQUE (instituicao_id, matricula)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_vinculo_usuario           ON vinculo_institucional (usuario_id);
CREATE INDEX ix_vinculo_campus            ON vinculo_institucional (campus_id);
CREATE INDEX ix_vinculo_curso             ON vinculo_institucional (curso_id);

-- vinculo_institucional (instituicao_id) ja e coberto pelo indice de uq_vinculo_matricula
