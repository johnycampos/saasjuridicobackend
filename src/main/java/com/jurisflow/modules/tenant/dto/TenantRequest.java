package com.jurisflow.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantRequest(
    @NotBlank(message = "Nome e obrigatorio")
    @Size(min = 2, max = 255)
    String nome,

    String cnpj,
    String telefone,
    String endereco
) {}
