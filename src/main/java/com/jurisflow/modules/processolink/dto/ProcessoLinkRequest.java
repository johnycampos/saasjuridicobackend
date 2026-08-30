package com.jurisflow.modules.processolink.dto;

import com.jurisflow.validation.SafeUrl;
import jakarta.validation.constraints.NotBlank;

public record ProcessoLinkRequest(
    @NotBlank(message = "Nome do arquivo e obrigatorio")
    String nomeArquivo,

    @NotBlank(message = "Link e obrigatorio")
    @SafeUrl
    String url
) {}
