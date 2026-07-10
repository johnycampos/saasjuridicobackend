package com.jurisflow.modules.tribunal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TribunalResponse(
    UUID id,
    String sigla,
    String nome,
    Boolean ativo,
    UUID createdBy,
    LocalDateTime createdAt
) {}
