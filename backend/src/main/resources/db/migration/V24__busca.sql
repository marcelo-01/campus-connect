-- =====================================================================
-- BUSCA DE POSTS E PESSOAS
-- Os posts sao buscados por palavras, em portugues, sem diferenciar
-- acento nem maiuscula: "bolsas" acha "bolsa", e "estagio" acha a
-- palavra com acento. As pessoas sao buscadas por pedaco do nome ou do
-- username, tambem sem acento (o unaccent fica na propria consulta).
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS unaccent;

-- O portugues do PostgreSQL com o unaccent antes do radical: a palavra
-- perde o acento e depois vira radical, no texto do post e na busca.
-- As palavras so com letras ASCII (asciiword) nao tem acento a tirar.
CREATE TEXT SEARCH CONFIGURATION busca_pt (COPY = pg_catalog.portuguese);
ALTER TEXT SEARCH CONFIGURATION busca_pt
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, portuguese_stem;

-- Calculada pelo banco a cada insert e update do texto. O Hibernate nao
-- mapeia a coluna, entao nunca escreve nela.
ALTER TABLE publicacao
    ADD COLUMN busca TSVECTOR GENERATED ALWAYS AS (to_tsvector('busca_pt', conteudo)) STORED;

CREATE INDEX ix_publicacao_busca ON publicacao USING GIN (busca) WHERE status = 'PUBLICADA';
