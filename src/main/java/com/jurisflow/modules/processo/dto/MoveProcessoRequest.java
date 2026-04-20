package com.jurisflow.modules.processo.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveProcessoRequest(
    @NotNull UUID targetColumnId,
    Integer newPosition
) {}
