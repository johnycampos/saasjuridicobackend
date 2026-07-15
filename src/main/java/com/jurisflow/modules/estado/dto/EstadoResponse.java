package com.jurisflow.modules.estado.dto;

import java.util.UUID;

public record EstadoResponse(
    UUID id,
    String sigla,
    String nome
) {}
