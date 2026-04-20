package com.jurisflow.modules.processo.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;
import com.jurisflow.modules.processo.ProcessoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProcessoResponse(
    UUID id,
    UUID tenantId,
    UUID groupId,
    UUID columnId,
    String titulo,
    String descricao,
    String numeroProcesso,
    String tipoAcao,
    String vara,
    String comarca,
    String tribunal,
    String autor,
    String reu,
    PrioridadeTipo prioridade,
    ProcessoStatus status,
    BigDecimal valorCausa,
    LocalDate dataDistribuicao,
    LocalDateTime prazoProximo,
    Integer posicaoColuna,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
