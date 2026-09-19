package com.jurisflow.modules.audiencia.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record AudienciaResponse(
    UUID id,
    UUID processoId,
    String processoNumero,
    String clienteNome,
    String titulo,
    String tipo,
    LocalDate data,
    LocalTime hora,
    String vara,
    String observacoes,
    String status,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
