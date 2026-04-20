-- Hibernate 6 não faz bind de String em tipos enum nativos do PostgreSQL.
-- Convertemos todas as colunas enum para VARCHAR mantendo os valores existentes.

ALTER TABLE tenant_members
    ALTER COLUMN role TYPE VARCHAR(20) USING role::VARCHAR;

ALTER TABLE group_members
    ALTER COLUMN role TYPE VARCHAR(20) USING role::VARCHAR;

ALTER TABLE processos
    ALTER COLUMN prioridade TYPE VARCHAR(20) USING prioridade::VARCHAR,
    ALTER COLUMN status     TYPE VARCHAR(20) USING status::VARCHAR;

ALTER TABLE invites
    ALTER COLUMN status TYPE VARCHAR(20) USING status::VARCHAR;

-- Remove os tipos enum nativos (CASCADE para remover dependências restantes)
DROP TYPE IF EXISTS tenant_role CASCADE;
DROP TYPE IF EXISTS group_role CASCADE;
DROP TYPE IF EXISTS prioridade_tipo CASCADE;
DROP TYPE IF EXISTS processo_status CASCADE;
DROP TYPE IF EXISTS invite_status CASCADE;
