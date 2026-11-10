-- =====================================================================
-- BOLHAS SEM INSTITUICAO
-- =====================================================================
-- Nada no sistema e oficialmente de uma instituicao: a hierarquia
-- (geral > campus > curso) vive so nas proprias bolhas, por bolha_pai_id.
-- Por isso saem as tabelas de instituicao, campus, curso e vinculo
-- institucional, e a bolha deixa de apontar para elas.

ALTER TABLE bolha
    DROP CONSTRAINT ck_bolha_escopo,
    DROP CONSTRAINT ck_bolha_entrada_automatica,
    DROP CONSTRAINT fk_bolha_campus,
    DROP CONSTRAINT fk_bolha_curso,
    DROP COLUMN instituicao_id,
    DROP COLUMN campus_id,
    DROP COLUMN curso_id,
    -- dependia do vinculo institucional para saber quem entra sozinho
    DROP COLUMN entrada_automatica;

DROP TABLE vinculo_institucional;
DROP TABLE curso;
DROP TABLE campus;
DROP TABLE instituicao;

-- A bolha do topo da hierarquia agora se chama "geral".
UPDATE bolha SET tipo = 'GERAL' WHERE tipo = 'INSTITUICAO';

ALTER TABLE bolha
    ADD COLUMN sigla     VARCHAR(10),
    ADD COLUMN endereco  VARCHAR(200),
    ADD COLUMN foto_url  TEXT,
    -- Quem fica dentro de quem (campus em geral, curso em campus) depende do
    -- tipo da outra linha e e conferido na aplicacao. Aqui fica o que da
    -- para garantir olhando so a propria linha.
    ADD CONSTRAINT ck_bolha_geral_sem_pai
        CHECK (tipo <> 'GERAL' OR bolha_pai_id IS NULL),
    ADD CONSTRAINT ck_bolha_campus_curso_com_pai
        CHECK (tipo NOT IN ('CAMPUS', 'CURSO') OR bolha_pai_id IS NOT NULL);
