-- =====================================================================
-- PUBLICACOES SALVAS E REPUBLICACOES (RF03)
-- =====================================================================

-- Salvar e privado: so quem salvou ve a lista.
CREATE TABLE publicacao_salva (
    usuario_id     UUID         NOT NULL REFERENCES usuario(id),
    publicacao_id  UUID         NOT NULL REFERENCES publicacao(id),
    salva_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, publicacao_id)
);

-- Republicar e publico: o post aparece no perfil de quem republicou.
CREATE TABLE republicacao (
    usuario_id     UUID         NOT NULL REFERENCES usuario(id),
    publicacao_id  UUID         NOT NULL REFERENCES publicacao(id),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, publicacao_id)
);

-- A chave primaria ja indexa usuario_id; falta o indice da outra FK.
CREATE INDEX ix_publicacao_salva_publicacao ON publicacao_salva (publicacao_id);
CREATE INDEX ix_republicacao_publicacao     ON republicacao (publicacao_id);

-- O perfil lista as republicacoes de uma pessoa da mais nova para a mais antiga.
CREATE INDEX ix_republicacao_usuario_data   ON republicacao (usuario_id, criado_em DESC);

-- =====================================================================
-- PREVIA DE LINK (RF02)
-- Uma midia do tipo LINK guarda o endereco em "url" e, quando o servidor
-- consegue ler a pagina, o titulo, a descricao, o nome do site e uma
-- copia da imagem de destaque (gravada por nos, em /api/arquivos).
-- =====================================================================
ALTER TABLE midia_publicacao
    ADD COLUMN titulo      VARCHAR(300),
    ADD COLUMN descricao   VARCHAR(500),
    ADD COLUMN site        VARCHAR(100),
    ADD COLUMN imagem_url  TEXT;

-- O feed do perfil filtra por autor e ordena pela data.
DROP INDEX ix_publicacao_autor;
CREATE INDEX ix_publicacao_autor ON publicacao (autor_id, criado_em DESC);
