package com.jurisflow.modules.mcp.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcrResponse(
    String client_id,
    String client_secret,
    long client_id_issued_at,
    long client_secret_expires_at,
    String client_name,
    List<String> redirect_uris,
    List<String> grant_types,
    List<String> response_types,
    String token_endpoint_auth_method
) {}
