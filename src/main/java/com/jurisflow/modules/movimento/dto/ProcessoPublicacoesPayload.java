package com.jurisflow.modules.movimento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ProcessoPublicacoesPayload(
    @NotNull(message = "tenantId e obrigatorio")
    UUID tenantId,

    @NotBlank(message = "numeroProcesso e obrigatorio")
    String numeroProcesso,

    @NotEmpty(message = "itens nao pode ser vazio")
    @Valid
    List<PublicacaoSyncItem> itens
) {}
