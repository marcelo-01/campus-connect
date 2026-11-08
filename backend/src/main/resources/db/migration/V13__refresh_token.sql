-- =====================================================================
-- REFRESH TOKEN
-- =====================================================================
-- Mantem o usuario logado depois que o JWT de acesso (curto) expira.
-- O token em si vai so para o cookie do navegador; aqui fica o hash
-- SHA-256 dele. Como o token tem 256 bits aleatorios, SHA-256 basta:
-- hash lento (Argon2) so faz sentido para senha escolhida por pessoa.
--
-- Cada uso troca o token por outro da mesma familia (rotacao). Se um
-- token ja trocado aparecer de novo, a familia inteira e revogada.
CREATE TABLE refresh_token (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    familia_id   UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 em hexadecimal
    persistente  BOOLEAN      NOT NULL,         -- "Manter conectado" marcado
    expira_em    TIMESTAMPTZ  NOT NULL,
    revogado_em  TIMESTAMPTZ,                   -- NULL = ainda vale
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_refresh_token_usuario     ON refresh_token (usuario_id);
CREATE INDEX ix_refresh_token_familia     ON refresh_token (familia_id);
