package com.jurisflow.modules.movimento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MovimentoSyncRequest(
    @NotEmpty(message = "processos nao pode ser vazio")
    @Valid
    List<ProcessoMovimentosPayload> processos
) {}
