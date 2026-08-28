package com.jurisflow.modules.movimento.dto;

import jakarta.validation.constraints.NotBlank;

public record MovimentoSyncItem(
    Integer codigo,

    @NotBlank(message = "Nome e obrigatorio")
    String nome,

    // string ISO-8601 com sufixo "Z" (ex: "2026-05-19T05:11:25.000Z") — parseada
    // explicitamente no service (ver MovimentoService.parseDataHora), nao como
    // LocalDateTime direto, pra nao depender de como o Jackson resolve o offset.
    @NotBlank(message = "DataHora e obrigatoria")
    String dataHora
) {}
