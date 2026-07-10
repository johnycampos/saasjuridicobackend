package com.jurisflow.modules.processolink.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProcessoLinkResponse(
    UUID id,
    UUID tenantId,
    UUID processoId,
    String nomeArquivo,
    String url,
    UUID createdBy,
    LocalDateTime createdAt
) {}
