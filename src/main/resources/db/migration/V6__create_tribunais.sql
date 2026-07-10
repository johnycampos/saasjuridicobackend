-- ============================================
-- TRIBUNAIS (lista global, compartilhada entre
-- todos os escritorios da plataforma - excecao
-- deliberada ao isolamento multi-tenant)
-- ============================================
CREATE TABLE tribunais (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sigla           VARCHAR(20) NOT NULL,
    nome            VARCHAR(255),
    ativo           BOOLEAN DEFAULT TRUE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_tribunais_sigla ON tribunais(sigla);

INSERT INTO tribunais (sigla, nome) VALUES
    ('TJ', 'Tribunal de Justica'),
    ('TRT', 'Tribunal Regional do Trabalho'),
    ('JF', 'Justica Federal');
