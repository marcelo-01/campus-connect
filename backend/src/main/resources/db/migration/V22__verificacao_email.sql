-- =====================================================================
-- VERIFICACAO DE E-MAIL
-- =====================================================================
-- A conta nasce com email_verificado = FALSE e so entra depois de
-- digitar o codigo de 6 digitos mandado para o e-mail.
--
-- As contas que ja existiam entraram antes dessa regra e ficam
-- verificadas, para ninguem que ja usa a rede ser interrompido.
UPDATE usuario SET email_verificado = TRUE;

-- Cada codigo mandado vira uma linha, e so o mais novo vale. As linhas
-- da ultima hora tambem servem para o limite de reenvio. Na verificacao,
-- as linhas da conta sao apagadas.
--
-- O codigo tem so 6 digitos, entao o SHA-256 nao impede quem tem o banco
-- de descobri-lo: ele so evita que o codigo fique legivel no banco e nos
-- backups. A protecao de verdade e o prazo de 15 minutos e o limite de
-- 5 tentativas por codigo.
CREATE TABLE verificacao_email (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    codigo_hash  VARCHAR(64)  NOT NULL,          -- SHA-256 em hexadecimal
    tentativas   SMALLINT     NOT NULL DEFAULT 0, -- codigos errados digitados
    expira_em    TIMESTAMPTZ  NOT NULL,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_verificacao_email_usuario ON verificacao_email (usuario_id, criado_em);
