package com.jurisflow.modules.mcp.oauth.dto;

import java.time.Instant;

public record OAuthConnectionResponse(
    String clientId,
    String clientName,
    Instant conectadoEm,
    Instant ultimoUso
) {}
