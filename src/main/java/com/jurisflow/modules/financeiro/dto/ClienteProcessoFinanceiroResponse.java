package com.jurisflow.modules.financeiro.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClienteProcessoFinanceiroResponse(
    UUID processoId,
    String numeroProcesso,
    String tipoAcao,
    UUID contratoId,
    BigDecimal valorTotal,
    String status,
    Integer parcelasPagas,
    Integer parcelasTotal
) {}
