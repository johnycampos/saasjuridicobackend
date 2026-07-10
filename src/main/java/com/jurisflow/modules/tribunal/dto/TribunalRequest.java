package com.jurisflow.modules.tribunal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TribunalRequest(
    @NotBlank(message = "Sigla e obrigatoria")
    @Size(min = 1, max = 20)
    String sigla,

    String nome
) {}
