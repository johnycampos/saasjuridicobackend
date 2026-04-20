package com.jurisflow.modules.board.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record BoardColumnRequest(
    @NotBlank(message = "Nome e obrigatorio")
    String nome,
    String cor,
    UUID groupId,
    Integer posicao
) {}
