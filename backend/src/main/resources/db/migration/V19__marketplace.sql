-- =====================================================================
-- CATEGORIAS DO MARKETPLACE (RF09, RF10)
-- Cada categoria e de servico ou de produto, e o anuncio so usa
-- categorias do proprio tipo. A lista e fixa: nao ha tela para
-- administrar categorias.
-- =====================================================================
ALTER TABLE categoria_anuncio ADD COLUMN tipo VARCHAR(20);

INSERT INTO categoria_anuncio (nome, slug, tipo) VALUES
    ('Tecnologia',               'tecnologia',             'SERVICO'),
    ('Design',                   'design',                 'SERVICO'),
    ('Escrita e revisão',        'escrita-revisao',        'SERVICO'),
    ('Aulas particulares',       'aulas-particulares',     'SERVICO'),
    ('Audiovisual',              'audiovisual',            'SERVICO'),
    ('Fotografia',               'fotografia',             'SERVICO'),
    ('Outros serviços',          'outros-servicos',        'SERVICO'),
    ('Livros e apostilas',       'livros-apostilas',       'PRODUTO'),
    ('Eletrônicos',              'eletronicos',            'PRODUTO'),
    ('Materiais e equipamentos', 'materiais-equipamentos', 'PRODUTO'),
    ('Roupas e acessórios',      'roupas-acessorios',      'PRODUTO'),
    ('Comida',                   'comida',                 'PRODUTO'),
    ('Outros produtos',          'outros-produtos',        'PRODUTO');

-- Categorias criadas antes desta migration, se houver, ficam como servico.
UPDATE categoria_anuncio SET tipo = 'SERVICO' WHERE tipo IS NULL;

ALTER TABLE categoria_anuncio ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE categoria_anuncio
    ADD CONSTRAINT ck_categoria_anuncio_tipo CHECK (tipo IN ('PRODUTO', 'SERVICO'));

-- =====================================================================
-- VITRINE DO MARKETPLACE (RF11, RF12)
-- A vitrine so mostra anuncios publicados, dos mais novos para os mais
-- antigos; pausados e removidos ficam fora do indice.
-- =====================================================================
CREATE INDEX ix_anuncio_publicados ON anuncio (publicado_em DESC, id DESC) WHERE status = 'PUBLICADO';
