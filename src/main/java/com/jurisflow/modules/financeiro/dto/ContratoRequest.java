package com.jurisflow.modules.financeiro.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContratoRequest(
    @NotNull(message = "Valor total e obrigatorio")
    BigDecimal valorTotal,

    String observacoes
) {}
