-- =====================================================================
-- NOTIFICACOES
-- =====================================================================
CREATE TABLE notificacao (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    destinatario_id  UUID         NOT NULL REFERENCES usuario(id),
    origem_id        UUID         REFERENCES usuario(id),
    tipo             VARCHAR(30)  NOT NULL,
    referencia_id    UUID,
    mensagem         VARCHAR(255),
    lida_em          TIMESTAMP,
    criado_em        TIMESTAMP    NOT NULL DEFAULT now()
);

-- =====================================================================
-- INDICES DAS CHAVES ESTRANGEIRAS
-- (o PostgreSQL nao cria indice automatico em FK)
-- =====================================================================
CREATE INDEX ix_notificacao_destinatario  ON notificacao (destinatario_id, lida_em);
