package com.jurisflow.modules.movimento.dto;

import com.jurisflow.modules.movimento.MovimentoOrigem;

import java.time.LocalDateTime;
import java.util.UUID;

public record MovimentoResponse(
    UUID id,
    UUID tenantId,
    UUID processoId,
    Integer codigo,
    String nome,
    LocalDateTime dataHora,
    String dadosExtras,
    Boolean visualizado,
    UUID visualizadoPor,
    LocalDateTime visualizadoEm,
    LocalDateTime createdAt,
    MovimentoOrigem origem
) {}
