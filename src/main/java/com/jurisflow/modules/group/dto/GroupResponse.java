package com.jurisflow.modules.group.dto;

import java.util.UUID;

public record GroupResponse(
    UUID id,
    UUID tenantId,
    String nome,
    String descricao,
    String cor,
    UUID createdBy,
    long totalProcessos
) {}
