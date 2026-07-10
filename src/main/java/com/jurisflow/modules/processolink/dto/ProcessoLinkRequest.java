package com.jurisflow.modules.processolink.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessoLinkRequest(
    @NotBlank(message = "Nome do arquivo e obrigatorio")
    String nomeArquivo,

    @NotBlank(message = "Link e obrigatorio")
    String url
) {}
