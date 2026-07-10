-- ============================================
-- Tribunal.java extends BaseEntity, que exige
-- created_at E updated_at (a V6 só criou created_at)
-- ============================================
ALTER TABLE tribunais ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
