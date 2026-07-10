ALTER TABLE tenant_members ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
-- Membros existentes já fizeram login e são considerados ativos
UPDATE tenant_members SET status = 'ACTIVE';
