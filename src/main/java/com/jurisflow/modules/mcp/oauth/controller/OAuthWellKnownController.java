package com.jurisflow.modules.mcp.oauth.controller;

import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OAuthWellKnownController {

    private final OAuthService oAuthService;

    @GetMapping(value = {"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"}, produces = "application/json")
    public ResponseEntity<Map<String, Object>> protectedResource() {
        String base = oAuthService.getPublicBackendUrl();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resource", base + "/mcp");
        map.put("authorization_servers", List.of(base));
        map.put("scopes_supported", List.of("mcp"));
        map.put("bearer_methods_supported", List.of("header"));
        return ResponseEntity.ok(map);
    }

    @GetMapping(value = {"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"}, produces = "application/json")
    public ResponseEntity<Map<String, Object>> authorizationServer() {
        String base = oAuthService.getPublicBackendUrl();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issuer", base);
        map.put("authorization_endpoint", base + "/oauth/authorize");
        map.put("token_endpoint", base + "/oauth/token");
        map.put("registration_endpoint", base + "/oauth/register");
        map.put("revocation_endpoint", base + "/oauth/revoke");
        map.put("response_types_supported", List.of("code"));
        map.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        map.put("code_challenge_methods_supported", List.of("S256"));
        map.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_post", "client_secret_basic"));
        map.put("scopes_supported", List.of("mcp"));
        return ResponseEntity.ok(map);
    }
}
