package com.jurisflow.modules.mcp.oauth.controller;

import com.jurisflow.modules.mcp.oauth.dto.DcrRequest;
import com.jurisflow.modules.mcp.oauth.dto.DcrResponse;
import com.jurisflow.modules.mcp.oauth.dto.OAuthTokenResponse;
import com.jurisflow.modules.mcp.oauth.exception.OAuthException;
import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthProtocolController {

    private final OAuthService oAuthService;

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@RequestBody DcrRequest request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        try {
            DcrResponse response = oAuthService.registerClient(request, ip);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (OAuthException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getError(), "error_description", e.getErrorDescription()));
        }
    }

    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam(name = "response_type", required = false) String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "resource", required = false) String resource
    ) {
        OAuthService.AuthorizeResult result = oAuthService.handleAuthorize(
                responseType, clientId, redirectUri, codeChallenge, codeChallengeMethod, state, scope, resource
        );

        if (result.isDirectHtmlError()) {
            String safeMsg = HtmlUtils.htmlEscape(result.htmlErrorMessage() != null ? result.htmlErrorMessage() : "Erro de autorização");
            String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Erro de Autorização</title></head>"
                    + "<body><h1>Erro de Autorização OAuth</h1><p>" + safeMsg + "</p></body></html>";
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML).body(html);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", result.redirectUrl())
                .build();
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(
            @RequestParam Map<String, String> params,
            @RequestHeader(name = "Authorization", required = false) String authHeader
    ) {
        try {
            String grantType = params.get("grant_type");
            OAuthTokenResponse response = oAuthService.handleToken(authHeader, grantType, params);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(response);
        } catch (OAuthException e) {
            return ResponseEntity.status(e.getStatus())
                    .header("Cache-Control", "no-store")
                    .body(Map.of("error", e.getError(), "error_description", e.getErrorDescription()));
        }
    }

    @PostMapping(value = "/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(@RequestParam(name = "token", required = false) String token) {
        oAuthService.revokeToken(token);
        return ResponseEntity.ok().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
