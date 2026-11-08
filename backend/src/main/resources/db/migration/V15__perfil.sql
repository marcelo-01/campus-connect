-- =====================================================================
-- PERFIL
-- =====================================================================
-- Campos do formulario "Editar perfil" que o banco ainda nao tinha.
-- A foto ja existia (foto_perfil_url); a capa segue o mesmo formato:
-- caminho publico da imagem servida por /api/arquivos.
ALTER TABLE usuario
    ADD COLUMN link     VARCHAR(200),
    ADD COLUMN capa_url TEXT;

-- Habilidades em ordem, como a pessoa digitou. "React" e "react" contam
-- como a mesma habilidade para o mesmo usuario.
CREATE TABLE habilidade_usuario (
    usuario_id  UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    ordem       INTEGER      NOT NULL,
    nome        VARCHAR(30)  NOT NULL,
    PRIMARY KEY (usuario_id, ordem),
    CONSTRAINT ck_habilidade_nome_preenchido CHECK (btrim(nome) <> '')
);

CREATE UNIQUE INDEX ux_habilidade_usuario_nome ON habilidade_usuario (usuario_id, lower(nome));
-- habilidade_usuario (usuario_id) ja e coberto pela chave primaria
