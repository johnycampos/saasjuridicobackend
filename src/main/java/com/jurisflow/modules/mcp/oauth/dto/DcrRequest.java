package com.jurisflow.modules.mcp.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcrRequest(
    String client_name,
    List<String> redirect_uris,
    String token_endpoint_auth_method,
    List<String> grant_types,
    List<String> response_types
) {}
