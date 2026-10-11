package com.jurisflow.modules.mcp.oauth.controller;

import com.jurisflow.modules.mcp.oauth.dto.OAuthConnectionResponse;
import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mcp/connections")
@RequiredArgsConstructor
public class OAuthConnectionsController {

    private final OAuthService oAuthService;

    @GetMapping
    public List<OAuthConnectionResponse> list(@AuthenticationPrincipal UserPrincipal user) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || user == null) throw BusinessException.forbidden();
        return oAuthService.listConnections(tenantId, user.getId());
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable("clientId") String clientId,
            @AuthenticationPrincipal UserPrincipal user
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || user == null) throw BusinessException.forbidden();
        oAuthService.revokeConnection(tenantId, user.getId(), clientId);
    }
}
