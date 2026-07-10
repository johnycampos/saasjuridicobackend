package com.jurisflow.modules.financeiro.dto;

import com.jurisflow.modules.financeiro.ParcelaStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ParcelaResponse(
    UUID id,
    UUID tenantId,
    UUID contratoId,
    Integer numero,
    BigDecimal valor,
    LocalDate vencimento,
    ParcelaStatus status,
    LocalDateTime dataPagamento,
    UUID pagoPor,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
