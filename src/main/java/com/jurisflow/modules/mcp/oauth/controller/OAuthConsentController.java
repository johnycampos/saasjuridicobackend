package com.jurisflow.modules.mcp.oauth.controller;

import com.jurisflow.modules.mcp.oauth.dto.OAuthApproveRequest;
import com.jurisflow.modules.mcp.oauth.dto.OAuthConsentDetailsResponse;
import com.jurisflow.modules.mcp.oauth.dto.OAuthRedirectResponse;
import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/oauth/requests")
@RequiredArgsConstructor
public class OAuthConsentController {

    private final OAuthService oAuthService;

    @GetMapping("/{req}")
    public ResponseEntity<OAuthConsentDetailsResponse> getRequestDetails(
            @PathVariable("req") String req,
            @AuthenticationPrincipal UserPrincipal user
    ) {
        if (user == null) throw BusinessException.forbidden();
        return ResponseEntity.ok(oAuthService.getConsentDetails(req, user.getId()));
    }

    @PostMapping("/{req}/approve")
    public ResponseEntity<OAuthRedirectResponse> approve(
            @PathVariable("req") String req,
            @RequestBody OAuthApproveRequest request,
            @AuthenticationPrincipal UserPrincipal user
    ) {
        if (user == null) throw BusinessException.forbidden();
        if (request == null || request.tenantId() == null) {
            throw new BusinessException("tenantId é obrigatório para aprovação");
        }
        return ResponseEntity.ok(oAuthService.approveConsent(req, request.tenantId(), user.getId()));
    }

    @PostMapping("/{req}/deny")
    public ResponseEntity<OAuthRedirectResponse> deny(
            @PathVariable("req") String req,
            @AuthenticationPrincipal UserPrincipal user
    ) {
        if (user == null) throw BusinessException.forbidden();
        return ResponseEntity.ok(oAuthService.denyConsent(req, user.getId()));
    }
}
