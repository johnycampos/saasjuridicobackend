-- ============================================
-- Remove prioridade/prazo_proximo de processos
-- (prioridade e prazo passam a existir por tarefa,
-- ver processo_tarefas; prazo_proximo nunca teve
-- campo no formulario, era um campo morto)
-- ============================================
ALTER TABLE processos DROP COLUMN IF EXISTS prioridade;
ALTER TABLE processos DROP COLUMN IF EXISTS prazo_proximo;
