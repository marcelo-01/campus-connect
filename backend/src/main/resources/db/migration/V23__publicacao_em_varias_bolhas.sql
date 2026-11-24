-- =====================================================================
-- PUBLICACAO EM VARIAS BOLHAS (RF02, RF08)
-- Quem publica marca as bolhas, e o post aparece so nelas: nao desce
-- mais para as bolhas de dentro. Cada post antigo fica na bolha em que
-- foi feito.
-- =====================================================================
CREATE TABLE publicacao_bolha (
    publicacao_id  UUID  NOT NULL REFERENCES publicacao(id) ON DELETE CASCADE,
    bolha_id       UUID  NOT NULL REFERENCES bolha(id),
    PRIMARY KEY (publicacao_id, bolha_id)
);

INSERT INTO publicacao_bolha (publicacao_id, bolha_id)
SELECT id, bolha_id FROM publicacao;

-- A chave primaria ja indexa publicacao_id; o feed de uma bolha parte da bolha.
CREATE INDEX ix_publicacao_bolha_bolha ON publicacao_bolha (bolha_id);

DROP INDEX ix_publicacao_bolha;
ALTER TABLE publicacao DROP COLUMN bolha_id;

-- Os feeds leem os posts publicados do mais novo para o mais antigo.
CREATE INDEX ix_publicacao_publicadas ON publicacao (criado_em DESC, id DESC) WHERE status = 'PUBLICADA';
