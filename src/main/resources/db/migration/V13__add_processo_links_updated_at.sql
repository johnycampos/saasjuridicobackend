-- ============================================
-- ProcessoLink.java segue o padrao TenantEntity
-- do projeto (exige updated_at), mas a V9 original
-- so criou created_at
-- ============================================
ALTER TABLE processo_links ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
