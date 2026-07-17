-- ============================================
-- Campo Estado no Processo
-- ============================================
ALTER TABLE processos ADD COLUMN IF NOT EXISTS estado VARCHAR(100);
