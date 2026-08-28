-- ============================================
-- PUBLICACOES (Comunica/DJEN) como nova origem
-- em processo_movimentos, ao lado de MOVIMENTO
-- (DataJud). Sem tabela nova, retrocompativel:
-- linhas ja existentes viram origem='MOVIMENTO'
-- automaticamente via DEFAULT.
--
-- codigo continua NULL pra publicacao, entao a
-- unique constraint de movimentos (processo_id,
-- codigo, data_hora), que ja existe desde a V20,
-- nunca barra duplicata de publicacao (NULL <>
-- NULL no Postgres) — por isso o indice parcial
-- abaixo, dedicado a hash_origem.
-- ============================================
ALTER TABLE processo_movimentos
  ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'MOVIMENTO',
  ADD COLUMN hash_origem VARCHAR(64);

CREATE UNIQUE INDEX uq_proc_mov_hash_origem
  ON processo_movimentos (processo_id, hash_origem)
  WHERE hash_origem IS NOT NULL;
