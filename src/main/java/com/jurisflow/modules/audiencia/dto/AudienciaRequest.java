package com.jurisflow.modules.audiencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AudienciaRequest(
    @NotNull(message = "Processo é obrigatório") UUID processoId,
    @NotBlank(message = "Título é obrigatório") @Size(min = 2, max = 500, message = "Título deve ter entre 2 e 500 caracteres") String titulo,
    @Size(max = 50) String tipo,
    @NotNull(message = "Data é obrigatória") LocalDate data,
    LocalTime hora,
    @Size(max = 255) String vara,
    String observacoes
) {}
