-- ============================================
-- Vinculo Processo -> Cliente
-- (aditivo apenas: a remocao das colunas titulo/
-- autor acontece junto com a mudanca do formulario
-- de processo, numa migration futura)
-- ============================================
ALTER TABLE processos ADD COLUMN IF NOT EXISTS cliente_id UUID REFERENCES clientes(id) ON DELETE SET NULL;

CREATE INDEX idx_processos_cliente ON processos(cliente_id);
