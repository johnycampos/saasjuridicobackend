package com.jurisflow.modules.tarefa.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleConcluidaRequest(
    @NotNull Boolean concluida
) {}
