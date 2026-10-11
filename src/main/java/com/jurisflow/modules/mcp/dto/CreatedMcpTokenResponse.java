package com.jurisflow.modules.mcp.dto;
import java.time.Instant;
import java.util.UUID;
public record CreatedMcpTokenResponse(UUID id, String nome, String token, String tokenPrefix, Instant expiresAt) {}
