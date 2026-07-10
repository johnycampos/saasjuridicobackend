package com.jurisflow.modules.processo.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    BigDecimal valorCausa,
    LocalDate dataDistribuicao,
    UUID groupId,
    UUID columnId
) {}
