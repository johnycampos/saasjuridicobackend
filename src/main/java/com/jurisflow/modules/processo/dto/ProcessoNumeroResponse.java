package com.jurisflow.modules.processo.dto;

import java.util.UUID;

/**
 * Usado pelo endpoint de integracao GET /api/integrations/processos, que
 * alimenta o script externo de sincronizacao de movimentos (ver
 * com.jurisflow.modules.movimento.MovimentoSyncController) com os dados
 * minimos necessarios pra consultar o DataJud por processo: o numero, o
 * tenant a quem pertence, e o tribunal (a API do DataJud e particionada por
 * tribunal).
 */
public record ProcessoNumeroResponse(
    UUID tenantId,
    UUID processoId,
    String numeroProcesso,
    String tribunal
) {}
