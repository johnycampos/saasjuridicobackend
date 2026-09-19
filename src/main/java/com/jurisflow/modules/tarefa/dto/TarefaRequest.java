package com.jurisflow.modules.tarefa.dto;

import com.jurisflow.modules.processo.PrioridadeTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record TarefaRequest(
    @NotBlank(message = "Titulo e obrigatorio")
    @Size(min = 2, max = 500)
    String titulo,

    String descricao,
    PrioridadeTipo prioridade,
    LocalDate prazo,
    LocalTime horaPrazo
) {}
