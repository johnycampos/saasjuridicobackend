package com.jurisflow.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResumoResponse(
    UUID proximoPrazoProcessoId,
    String proximoPrazoNumeroProcesso,
    String proximoPrazoClienteNome,
    LocalDate proximoPrazoData,
    BigDecimal valorPagoTotal,
    List<AniversarianteResponse> aniversariantesDoMes
) {}
