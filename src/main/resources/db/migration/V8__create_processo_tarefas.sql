-- ============================================
-- TAREFAS DO PROCESSO
-- (aditivo apenas: a remocao da coluna prioridade
-- de "processos" acontece junto com a mudanca do
-- formulario/detalhe de processo, numa migration futura)
-- ============================================
CREATE TABLE processo_tarefas (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    titulo          VARCHAR(500) NOT NULL,
    descricao       TEXT,
    prioridade      VARCHAR(20) DEFAULT 'MEDIA',
    prazo           DATE,
    concluida       BOOLEAN DEFAULT FALSE,
    concluida_em    TIMESTAMPTZ,
    concluida_por   UUID REFERENCES users(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_processo_tarefas_processo ON processo_tarefas(processo_id);
CREATE INDEX idx_processo_tarefas_tenant ON processo_tarefas(tenant_id);
CREATE INDEX idx_processo_tarefas_prazo ON processo_tarefas(prazo);
