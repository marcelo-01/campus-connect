-- =====================================================================
-- REDEFINICAO DE SENHA
-- =====================================================================
-- O "Esqueceu a senha?" manda um codigo de 6 digitos para o e-mail, como
-- a verificacao do cadastro, e guarda o codigo na mesma tabela. A coluna
-- nova diz para que serve cada codigo: o da verificacao nao troca a
-- senha, e o da redefinicao nao verifica o e-mail. O limite de envios
-- tambem conta cada finalidade separada.
--
-- Os codigos que ja existiam sao todos da verificacao.
ALTER TABLE verificacao_email
    ADD COLUMN finalidade VARCHAR(20) NOT NULL DEFAULT 'VERIFICACAO_EMAIL';
ALTER TABLE verificacao_email ALTER COLUMN finalidade DROP DEFAULT;
ALTER TABLE verificacao_email
    ADD CONSTRAINT ck_verificacao_email_finalidade
    CHECK (finalidade IN ('VERIFICACAO_EMAIL', 'REDEFINICAO_SENHA'));

-- As buscas sao sempre pela conta e pela finalidade, do mais novo para o
-- mais antigo.
DROP INDEX ix_verificacao_email_usuario;
CREATE INDEX ix_verificacao_email_usuario ON verificacao_email (usuario_id, finalidade, criado_em);
