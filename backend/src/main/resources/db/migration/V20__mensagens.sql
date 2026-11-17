-- =====================================================================
-- CONVERSAS (RF05)
-- A lista de conversas vem da mais recente para a mais antiga. A data
-- da ultima mensagem fica na conversa para ordenar e paginar a lista;
-- enquanto ela e nula, a conversa esta vazia e nao aparece para
-- ninguem.
-- =====================================================================
ALTER TABLE conversa ADD COLUMN ultima_mensagem_em TIMESTAMPTZ;

-- =====================================================================
-- MENSAGENS (RF05)
-- Apagar uma mensagem tira o texto do banco: so ficam a data e quem
-- mandou, para a conversa mostrar "Mensagem apagada" no lugar dela.
-- =====================================================================
ALTER TABLE mensagem DROP CONSTRAINT ck_mensagem_conteudo;
ALTER TABLE mensagem ADD CONSTRAINT ck_mensagem_conteudo
    CHECK (conteudo IS NOT NULL OR anexo_url IS NOT NULL OR removido_em IS NOT NULL);
ALTER TABLE mensagem ADD CONSTRAINT ck_mensagem_apagada_sem_texto
    CHECK (removido_em IS NULL OR conteudo IS NULL);
ALTER TABLE mensagem ADD CONSTRAINT ck_mensagem_tamanho
    CHECK (char_length(conteudo) <= 2000);

-- A pagina de mensagens, a ultima mensagem de cada conversa e as nao
-- lidas andam por (conversa, enviado_em, id).
DROP INDEX ix_mensagem_conversa;
CREATE INDEX ix_mensagem_conversa ON mensagem (conversa_id, enviado_em, id);

-- O limite de envio conta as mensagens recentes de quem manda.
DROP INDEX ix_mensagem_remetente;
CREATE INDEX ix_mensagem_remetente ON mensagem (remetente_id, enviado_em);

-- =====================================================================
-- BLOQUEIO (RF04, RF05)
-- Cada pessoa bloqueia outra por conta propria: se as duas se
-- bloqueiam, sao dois registros, e desbloquear de um lado nao desfaz o
-- bloqueio do outro. Por isso o bloqueio nao cabe na linha unica do par
-- em conexao.
-- =====================================================================
CREATE TABLE bloqueio (
    bloqueador_id  UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    bloqueado_id   UUID         NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (bloqueador_id, bloqueado_id),
    CONSTRAINT ck_bloqueio_outra_pessoa CHECK (bloqueador_id <> bloqueado_id)
);

CREATE INDEX ix_bloqueio_bloqueado ON bloqueio (bloqueado_id);
