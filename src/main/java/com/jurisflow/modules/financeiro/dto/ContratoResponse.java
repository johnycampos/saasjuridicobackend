package com.jurisflow.modules.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContratoResponse(
    UUID id,
    UUID tenantId,
    UUID processoId,
    BigDecimal valorTotal,
    String observacoes,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ParcelaResponse> parcelas
) {}
