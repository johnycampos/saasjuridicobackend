-- ============================================
-- CLIENTES (cadastro por escritorio)
-- ============================================
CREATE TABLE clientes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nome            VARCHAR(255) NOT NULL,
    cpf_cnpj        VARCHAR(20),
    telefone        VARCHAR(20),
    email           VARCHAR(255),
    endereco        TEXT,
    observacoes     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_clientes_tenant ON clientes(tenant_id);
CREATE INDEX idx_clientes_nome ON clientes(tenant_id, nome);
