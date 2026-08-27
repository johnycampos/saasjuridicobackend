package com.jurisflow.modules.movimento.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleVisualizadoRequest(
    @NotNull Boolean visualizado
) {}
