-- O constraint UNIQUE em tenant_id ja cria um indice implicito;
-- o idx_tgc_tenant explicito eh redundante.
DROP INDEX IF EXISTS idx_tgc_tenant;
