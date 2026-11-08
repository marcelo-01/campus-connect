-- =====================================================================
-- USERNAME NORMALIZADO
-- =====================================================================
-- Mesmo criterio do e-mail (V11): o username e gravado sem espacos nas
-- pontas e em minusculas, entao o UNIQUE de usuario.username impede
-- contas como "Ana" e "ana".
UPDATE usuario
   SET username = lower(btrim(username))
 WHERE username <> lower(btrim(username));

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_username_normalizado CHECK (username = lower(btrim(username)));
