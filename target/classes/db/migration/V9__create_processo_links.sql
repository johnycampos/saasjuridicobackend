-- ============================================
-- LINKS DE ARQUIVOS DO PROCESSO
-- (Google Drive ou qualquer outro local)
-- ============================================
CREATE TABLE processo_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    nome_arquivo    VARCHAR(500) NOT NULL,
    url             VARCHAR(1000) NOT NULL,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_processo_links_processo ON processo_links(processo_id);
