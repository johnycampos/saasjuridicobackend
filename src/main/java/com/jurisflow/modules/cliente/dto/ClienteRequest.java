package com.jurisflow.modules.cliente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
    @NotBlank(message = "Nome e obrigatorio")
    @Size(min = 2, max = 255)
    String nome,

    String cpfCnpj,
    String telefone,
    String email,
    String endereco,
    String observacoes
) {}
