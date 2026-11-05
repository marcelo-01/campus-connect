-- =====================================================================
-- DATAS COM FUSO HORARIO
-- =====================================================================
-- TIMESTAMP guarda data e hora sem dizer de qual fuso elas sao.
-- TIMESTAMPTZ guarda o instante exato. Valores antigos sao lidos como UTC.
ALTER TABLE instituicao
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE campus
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE curso
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE usuario
    ALTER COLUMN ultimo_acesso_em TYPE TIMESTAMPTZ USING ultimo_acesso_em AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em        TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em    TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC',
    ALTER COLUMN anonimizado_em   TYPE TIMESTAMPTZ USING anonimizado_em AT TIME ZONE 'UTC';

ALTER TABLE vinculo_institucional
    ALTER COLUMN verificado_em TYPE TIMESTAMPTZ USING verificado_em AT TIME ZONE 'UTC',
    ALTER COLUMN encerrado_em  TYPE TIMESTAMPTZ USING encerrado_em AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE conexao
    ALTER COLUMN solicitado_em TYPE TIMESTAMPTZ USING solicitado_em AT TIME ZONE 'UTC',
    ALTER COLUMN respondido_em TYPE TIMESTAMPTZ USING respondido_em AT TIME ZONE 'UTC';

ALTER TABLE notificacao
    ALTER COLUMN lida_em   TYPE TIMESTAMPTZ USING lida_em AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE bolha
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE membro_bolha
    ALTER COLUMN entrou_em TYPE TIMESTAMPTZ USING entrou_em AT TIME ZONE 'UTC';

ALTER TABLE anuncio
    ALTER COLUMN publicado_em  TYPE TIMESTAMPTZ USING publicado_em AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC';

ALTER TABLE avaliacao_anuncio
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE favorito_anuncio
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE anuncio_bolha
    ALTER COLUMN vinculado_em TYPE TIMESTAMPTZ USING vinculado_em AT TIME ZONE 'UTC';

ALTER TABLE impulsionamento
    ALTER COLUMN periodo_inicio TYPE TIMESTAMPTZ USING periodo_inicio AT TIME ZONE 'UTC',
    ALTER COLUMN periodo_fim    TYPE TIMESTAMPTZ USING periodo_fim AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em      TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE publicacao
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC',
    ALTER COLUMN removido_em   TYPE TIMESTAMPTZ USING removido_em AT TIME ZONE 'UTC';

ALTER TABLE comentario
    ALTER COLUMN criado_em     TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC',
    ALTER COLUMN atualizado_em TYPE TIMESTAMPTZ USING atualizado_em AT TIME ZONE 'UTC',
    ALTER COLUMN removido_em   TYPE TIMESTAMPTZ USING removido_em AT TIME ZONE 'UTC';

ALTER TABLE midia_publicacao
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE reacao
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE conversa
    ALTER COLUMN criado_em TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';

ALTER TABLE participante_conversa
    ALTER COLUMN entrou_em         TYPE TIMESTAMPTZ USING entrou_em AT TIME ZONE 'UTC',
    ALTER COLUMN ultima_leitura_em TYPE TIMESTAMPTZ USING ultima_leitura_em AT TIME ZONE 'UTC';

ALTER TABLE mensagem
    ALTER COLUMN enviado_em  TYPE TIMESTAMPTZ USING enviado_em AT TIME ZONE 'UTC',
    ALTER COLUMN editado_em  TYPE TIMESTAMPTZ USING editado_em AT TIME ZONE 'UTC',
    ALTER COLUMN removido_em TYPE TIMESTAMPTZ USING removido_em AT TIME ZONE 'UTC';

ALTER TABLE denuncia
    ALTER COLUMN resolvido_em TYPE TIMESTAMPTZ USING resolvido_em AT TIME ZONE 'UTC',
    ALTER COLUMN criado_em    TYPE TIMESTAMPTZ USING criado_em AT TIME ZONE 'UTC';
