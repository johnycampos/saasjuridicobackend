-- ============================================
-- Estado.java extends BaseEntity, que exige
-- created_at E updated_at (a V16 só criou created_at)
-- ============================================
ALTER TABLE estados ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
