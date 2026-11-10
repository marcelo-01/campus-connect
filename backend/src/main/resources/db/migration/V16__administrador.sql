-- =====================================================================
-- ADMINISTRADOR
-- =====================================================================
-- Administradores criam, editam e excluem bolhas. Por enquanto sao so
-- a equipe do projeto, e nao ha tela para promover alguem. Para dar a
-- permissao, rode no banco:
--
--   UPDATE usuario SET administrador = TRUE WHERE email = 'pessoa@exemplo.com';
--
-- A mudanca vale na proxima vez que a pessoa abrir ou recarregar o site.
ALTER TABLE usuario
    ADD COLUMN administrador BOOLEAN NOT NULL DEFAULT FALSE;
