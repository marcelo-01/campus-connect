-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS QUE FALTAVAM
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_conexao_solicitante       ON conexao (solicitante_id);
CREATE INDEX ix_notificacao_origem        ON notificacao (origem_id);
CREATE INDEX ix_conversa_maior            ON conversa (usuario_maior_id);
CREATE INDEX ix_denuncia_publicacao       ON denuncia (publicacao_id);
CREATE INDEX ix_denuncia_comentario       ON denuncia (comentario_id);
CREATE INDEX ix_denuncia_anuncio          ON denuncia (anuncio_id);
CREATE INDEX ix_denuncia_usuario_alvo     ON denuncia (usuario_alvo_id);
CREATE INDEX ix_denuncia_moderador        ON denuncia (moderador_id);
