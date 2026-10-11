package com.jurisflow.modules.mcp.server;
import com.jurisflow.security.UserPrincipal;
import java.util.UUID;
public record McpCallContext(UUID tenantId, UserPrincipal principal) {
    public UUID userId() { return principal.getId(); }
}
