package com.jurisflow.modules.mcp;
import com.jurisflow.modules.mcp.dto.*;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/mcp/tokens")
@RequiredArgsConstructor
public class McpTokenController {
    private final McpTokenService service;
    @GetMapping
    public List<McpTokenResponse> list(@AuthenticationPrincipal UserPrincipal user) { return service.list(user.getId()); }
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CreatedMcpTokenResponse create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody CreateMcpTokenRequest request) {
        return service.create(user.getId(), request);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) { service.revoke(id, user.getId()); }
}
