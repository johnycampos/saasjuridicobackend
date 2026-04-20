package com.jurisflow.modules.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(
    @NotBlank(message = "Nome e obrigatorio")
    @Size(min = 2, max = 255)
    String nome,

    String descricao,
    String cor
) {}
