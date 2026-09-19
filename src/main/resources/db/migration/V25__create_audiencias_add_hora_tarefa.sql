-- ============================================
-- AUDIENCIAS — compromissos juridicos com
-- data, hora, tipo e vara vinculados a processo
-- ============================================
CREATE TABLE audiencias (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    titulo          VARCHAR(500) NOT NULL,
    tipo            VARCHAR(50) NOT NULL DEFAULT 'AUDIENCIA',
    data            DATE NOT NULL,
    hora            TIME,
    vara            VARCHAR(255),
    observacoes     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'AGENDADA',
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_aud_tenant ON audiencias(tenant_id);
CREATE INDEX idx_aud_data ON audiencias(tenant_id, data);
CREATE INDEX idx_aud_status ON audiencias(tenant_id, status);

-- ============================================
-- HORA NA TAREFA — campo opcional para tarefas
-- que tem horario especifico (ex: audiencia,
-- pericia). Quando NULL, eh evento de dia inteiro.
-- ============================================
ALTER TABLE processo_tarefas ADD COLUMN IF NOT EXISTS hora_prazo TIME;
