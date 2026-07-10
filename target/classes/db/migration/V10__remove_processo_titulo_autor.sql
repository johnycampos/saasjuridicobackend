-- ============================================
-- Remove titulo/autor de processos
-- (Cliente passa a ser o identificador do processo)
-- ============================================
DROP INDEX IF EXISTS idx_processos_search;

ALTER TABLE processos DROP COLUMN IF EXISTS titulo;
ALTER TABLE processos DROP COLUMN IF EXISTS autor;

CREATE INDEX idx_processos_search ON processos
    USING GIN (to_tsvector('portuguese',
        coalesce(numero_processo,'') || ' ' ||
        coalesce(descricao,'') || ' ' ||
        coalesce(reu,'')
    ));
