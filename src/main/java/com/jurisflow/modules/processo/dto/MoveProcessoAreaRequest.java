package com.jurisflow.modules.processo.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveProcessoAreaRequest(
    @NotNull(message = "Area e obrigatoria")
    UUID groupId
) {}
