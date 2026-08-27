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
    UUID clienteId,
    String clienteNome,
    String clienteTelefone,
    String descricao,
    String numeroProcesso,
    String tipoAcao,
    String vara,
    String comarca,
    String estado,
    String tribunal,
    String reu,
    ProcessoStatus status,
    BigDecimal valorCausa,
    LocalDate dataDistribuicao,
    LocalDate proximaTarefaPrazo,
    PrioridadeTipo prioridadeMaisUrgente,
    LocalDateTime ultimaMovimentacao,
    Boolean temMovimentacaoNaoLida,
    Integer posicaoColuna,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
