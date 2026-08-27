-- ============================================
-- MOVIMENTACOES DO PROCESSO
-- (andamentos vindos de tribunal/DataJud; a carga
-- dos dados acontece por script/import externo,
-- nao ha endpoint de escrita em lote por enquanto)
--
-- dados_extras fica em TEXT, nao JSONB nativo,
-- seguindo a mesma convencao ja usada em
-- processos.campos_extras (ver V2/V3) para nao
-- precisar de um Hibernate UserType customizado.
-- ============================================
CREATE TABLE processo_movimentos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    codigo          INT,
    nome            VARCHAR(500) NOT NULL,
    data_hora       TIMESTAMPTZ NOT NULL,
    dados_extras    TEXT DEFAULT '{}',
    visualizado     BOOLEAN NOT NULL DEFAULT FALSE,
    visualizado_por UUID REFERENCES users(id) ON DELETE SET NULL,
    visualizado_em  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(processo_id, codigo, data_hora)
);

CREATE INDEX idx_proc_mov_processo ON processo_movimentos(processo_id);
CREATE INDEX idx_proc_mov_tenant ON processo_movimentos(tenant_id);
CREATE INDEX idx_proc_mov_data_hora ON processo_movimentos(data_hora DESC);
CREATE INDEX idx_proc_mov_codigo ON processo_movimentos(codigo);
CREATE INDEX idx_proc_mov_nao_visualizado ON processo_movimentos(processo_id) WHERE visualizado = false;
