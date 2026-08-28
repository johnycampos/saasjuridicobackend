package com.jurisflow.modules.movimento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PublicacaoSyncRequest(
    @NotEmpty(message = "publicacoes nao pode ser vazio")
    @Valid
    List<ProcessoPublicacoesPayload> publicacoes
) {}
