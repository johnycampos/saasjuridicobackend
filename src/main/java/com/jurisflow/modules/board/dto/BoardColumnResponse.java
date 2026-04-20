package com.jurisflow.modules.board.dto;

import com.jurisflow.modules.processo.dto.ProcessoResponse;

import java.util.List;
import java.util.UUID;

public record BoardColumnResponse(
    UUID id,
    UUID tenantId,
    UUID groupId,
    String nome,
    Integer posicao,
    String cor,
    List<ProcessoResponse> processos
) {}
