package com.jurisflow.modules.dashboard.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;

import java.time.LocalDate;
import java.util.UUID;

public record AgendaTarefaResponse(
    UUID id,
    UUID processoId,
    String processoNumero,
    String clienteNome,
    String titulo,
    PrioridadeTipo prioridade,
    LocalDate prazo
) {}
