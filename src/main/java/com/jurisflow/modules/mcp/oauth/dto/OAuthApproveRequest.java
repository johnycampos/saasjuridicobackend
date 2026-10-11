package com.jurisflow.modules.mcp.oauth.dto;

import java.util.UUID;

public record OAuthApproveRequest(
    UUID tenantId
) {}
