-- =====================================================================
-- CampusConnect - Modelo Fisico do Banco de Dados
-- SGBD: PostgreSQL 13+ (gen_random_uuid() nativo)
-- 24 tabelas em 9 familias
--
-- Regra de negocio: qualquer pessoa pode criar conta e entrar em
-- qualquer bolha. O vinculo com uma instituicao e OPCIONAL e fica em
-- uma tabela propria (vinculo_institucional), podendo um usuario ter
-- zero, um ou varios vinculos.
-- =====================================================================

-- =====================================================================
-- INSTITUICAO
-- =====================================================================
CREATE TABLE instituicao (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(150) NOT NULL,
    sigla           VARCHAR(20),
    criado_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMP
);

CREATE TABLE campus (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    instituicao_id  UUID         NOT NULL REFERENCES instituicao(id),
    nome            VARCHAR(120) NOT NULL,
    cidade          VARCHAR(120),
    uf              CHAR(2),
    criado_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMP,
    -- alvo das FKs compostas: garante que o campus pertence a instituicao informada
    CONSTRAINT uq_campus_instituicao UNIQUE (id, instituicao_id)
);

CREATE TABLE curso (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    campus_id       UUID         NOT NULL REFERENCES campus(id),
    nome            VARCHAR(120) NOT NULL,
    grau            VARCHAR(20),
    criado_em       TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMP,
    -- alvo das FKs compostas: garante que o curso pertence ao campus informado
    CONSTRAINT uq_curso_campus UNIQUE (id, campus_id)
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_campus_instituicao        ON campus (instituicao_id);
CREATE INDEX ix_curso_campus              ON curso (campus_id);
