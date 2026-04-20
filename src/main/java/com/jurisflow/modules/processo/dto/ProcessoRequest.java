package com.jurisflow.modules.processo.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProcessoRequest(
    @NotBlank(message = "Titulo e obrigatorio")
    @Size(min = 3, max = 500)
    String titulo,

    String descricao,
    String numeroProcesso,
    String tipoAcao,
    String vara,
    String comarca,
    String tribunal,
    String autor,
    String reu,
    PrioridadeTipo prioridade,
    BigDecimal valorCausa,
    LocalDate dataDistribuicao,
    LocalDateTime prazoProximo,
    UUID groupId,
    UUID columnId
) {}
