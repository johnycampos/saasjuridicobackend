package com.jurisflow.modules.processo.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProcessoRequest(
    @NotNull(message = "Cliente e obrigatorio")
    UUID clienteId,

    String descricao,
    String numeroProcesso,
    String tipoAcao,
    String vara,
    String comarca,
    String tribunal,
    String reu,
    PrioridadeTipo prioridade,
    BigDecimal valorCausa,
    LocalDate dataDistribuicao,
    LocalDateTime prazoProximo,
    UUID groupId,
    UUID columnId
) {}
