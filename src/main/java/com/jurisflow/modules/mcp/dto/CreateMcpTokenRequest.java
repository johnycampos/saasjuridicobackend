package com.jurisflow.modules.mcp.dto;
import jakarta.validation.constraints.*;
public record CreateMcpTokenRequest(
    @NotBlank @Size(max = 100) String nome,
    @Min(1) @Max(365) Integer expiresInDays
) {}
