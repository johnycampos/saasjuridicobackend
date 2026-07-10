-- ============================================
-- PARCELAS do contrato
-- Historico de pagamento simples: status +
-- data_pagamento (mesmo padrao de processo_tarefas)
-- ============================================
CREATE TABLE parcelas (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contrato_id     UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    numero          INT NOT NULL DEFAULT 1,
    valor           DECIMAL(15,2) NOT NULL,
    vencimento      DATE,
    status          VARCHAR(20) DEFAULT 'PENDENTE',
    data_pagamento  TIMESTAMPTZ,
    pago_por        UUID REFERENCES users(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_parcelas_contrato ON parcelas(contrato_id);
CREATE INDEX idx_parcelas_tenant ON parcelas(tenant_id);
