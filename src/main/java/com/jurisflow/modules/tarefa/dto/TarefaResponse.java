package com.jurisflow.modules.tarefa.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TarefaResponse(
    UUID id,
    UUID tenantId,
    UUID processoId,
    String titulo,
    String descricao,
    PrioridadeTipo prioridade,
    LocalDate prazo,
    Boolean concluida,
    LocalDateTime concluidaEm,
    UUID concluidaPor,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
