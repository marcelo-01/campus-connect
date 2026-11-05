-- =====================================================================
-- E-MAIL NORMALIZADO
-- =====================================================================
-- O e-mail e sempre gravado sem espacos nas pontas e em minusculas. Assim
-- o UNIQUE de usuario.email passa a ignorar a diferenca entre maiusculas e
-- minusculas, e buscas simples por email continuam usando esse indice.
UPDATE usuario
   SET email = lower(btrim(email))
 WHERE email <> lower(btrim(email));

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_email_normalizado CHECK (email = lower(btrim(email)));
