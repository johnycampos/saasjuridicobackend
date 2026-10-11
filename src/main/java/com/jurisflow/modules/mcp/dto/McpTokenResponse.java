package com.jurisflow.modules.mcp.dto;
import java.time.*;
import java.util.UUID;
public record McpTokenResponse(UUID id, String nome, String tokenPrefix,
    LocalDateTime createdAt, Instant lastUsedAt, Instant expiresAt, boolean revoked) {}
