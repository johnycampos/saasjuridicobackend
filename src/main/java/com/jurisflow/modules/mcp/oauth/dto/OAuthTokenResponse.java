package com.jurisflow.modules.mcp.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthTokenResponse(
    String access_token,
    String token_type,
    long expires_in,
    String refresh_token,
    String scope
) {}
