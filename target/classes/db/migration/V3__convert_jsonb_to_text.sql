-- Hibernate 6 não faz bind de String em colunas jsonb.
-- Convertemos para TEXT mantendo compatibilidade com JSON válido.

ALTER TABLE processos
    ALTER COLUMN campos_extras TYPE TEXT USING campos_extras::TEXT;

ALTER TABLE processo_historico
    ALTER COLUMN dados_anteriores TYPE TEXT USING dados_anteriores::TEXT,
    ALTER COLUMN dados_novos      TYPE TEXT USING dados_novos::TEXT;
