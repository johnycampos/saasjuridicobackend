package com.jurisflow.modules.mcp.oauth.dto;

import java.util.List;
import java.util.UUID;

public record OAuthConsentDetailsResponse(
    String clientName,
    String redirectHost,
    String scope,
    List<TenantOption> tenants
) {
    public record TenantOption(UUID id, String nome, String role) {}
}
