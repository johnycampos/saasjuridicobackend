-- ============================================
-- CONTRATOS (honorarios do escritorio por processo)
-- Um contrato por processo.
-- ============================================
CREATE TABLE contratos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID NOT NULL UNIQUE REFERENCES processos(id) ON DELETE CASCADE,
    valor_total     DECIMAL(15,2) NOT NULL,
    observacoes     TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_contratos_tenant ON contratos(tenant_id);
