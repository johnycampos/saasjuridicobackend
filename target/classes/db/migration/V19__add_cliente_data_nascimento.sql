-- ============================================
-- Data de aniversario do cliente
-- ============================================
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS data_nascimento DATE;
