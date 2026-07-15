-- ============================================
-- ESTADOS (lista global, compartilhada entre
-- todos os escritorios da plataforma - mesma
-- excecao ao isolamento multi-tenant usada em
-- tribunais)
-- ============================================
CREATE TABLE estados (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sigla           VARCHAR(2) NOT NULL,
    nome            VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_estados_sigla ON estados(sigla);

INSERT INTO estados (sigla, nome) VALUES
    ('AC', 'Acre'),
    ('AP', 'Amapá'),
    ('AM', 'Amazonas'),
    ('PA', 'Pará'),
    ('RO', 'Rondônia'),
    ('RR', 'Roraima'),
    ('TO', 'Tocantins'),
    ('AL', 'Alagoas'),
    ('BA', 'Bahia'),
    ('CE', 'Ceará'),
    ('MA', 'Maranhão'),
    ('PB', 'Paraíba'),
    ('PE', 'Pernambuco'),
    ('PI', 'Piauí'),
    ('RN', 'Rio Grande do Norte'),
    ('SE', 'Sergipe'),
    ('GO', 'Goiás'),
    ('MT', 'Mato Grosso'),
    ('MS', 'Mato Grosso do Sul'),
    ('DF', 'Distrito Federal'),
    ('ES', 'Espírito Santo'),
    ('MG', 'Minas Gerais'),
    ('RJ', 'Rio de Janeiro'),
    ('SP', 'São Paulo'),
    ('PR', 'Paraná'),
    ('RS', 'Rio Grande do Sul'),
    ('SC', 'Santa Catarina');
