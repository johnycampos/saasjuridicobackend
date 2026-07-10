package com.jurisflow.modules.financeiro.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaRequest(
    Integer numero,

    @NotNull(message = "Valor e obrigatorio")
    BigDecimal valor,

    LocalDate vencimento
) {}
