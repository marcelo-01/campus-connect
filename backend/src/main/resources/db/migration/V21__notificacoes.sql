-- =====================================================================
-- NOTIFICACOES
-- Cada acao que avisa alguem vira uma linha: pedido de conexao, pedido
-- aceito, comentario, resposta, curtida, republicacao e avaliacao de
-- anuncio. A tela junta as curtidas e as republicacoes do mesmo alvo
-- numa notificacao so ("Ana e mais 3 pessoas curtiram").
--
-- referencia_id e a pagina para onde a notificacao leva (a publicacao
-- ou o anuncio). detalhe_id e o que foi curtido ou escrito la dentro (o
-- comentario ou a avaliacao). Nenhuma das duas tem FK, porque apontam
-- para tabelas diferentes conforme o tipo: quando o alvo some, o modulo
-- de notificacoes apaga as linhas dele.
-- =====================================================================
ALTER TABLE notificacao ADD COLUMN detalhe_id UUID;

-- O texto do comentario, que nao muda depois de escrito, fica copiado
-- aqui para a tela nao precisar buscar.
ALTER TABLE notificacao RENAME COLUMN mensagem TO trecho;

-- Uma pessoa avisa uma vez so por alvo: curtir, descurtir e curtir de
-- novo nao avisam de novo. Nos pedidos de conexao, pedir de novo depois
-- de desfazer a conexao reaproveita a linha. As notificacoes de conexao
-- nao tem referencia, dai o NULLS NOT DISTINCT. Comentarios ficam de
-- fora: cada um e uma notificacao.
CREATE UNIQUE INDEX ux_notificacao_uma_por_pessoa
    ON notificacao (destinatario_id, tipo, referencia_id, detalhe_id, origem_id) NULLS NOT DISTINCT
    WHERE tipo IN ('SOLICITACAO_CONEXAO', 'CONEXAO_ACEITA', 'CURTIDA_PUBLICACAO', 'CURTIDA_COMENTARIO',
                   'REPUBLICACAO', 'AVALIACAO_ANUNCIO');

-- A tela anda pelas notificacoes da pessoa da mais nova para a mais
-- antiga; o contador olha so as nao lidas.
DROP INDEX ix_notificacao_destinatario;
CREATE INDEX ix_notificacao_destinatario ON notificacao (destinatario_id, criado_em);
CREATE INDEX ix_notificacao_nao_lidas    ON notificacao (destinatario_id) WHERE lida_em IS NULL;

-- Quando a publicacao, o comentario, o anuncio ou a avaliacao some.
CREATE INDEX ix_notificacao_referencia ON notificacao (referencia_id);
CREATE INDEX ix_notificacao_detalhe    ON notificacao (detalhe_id);
