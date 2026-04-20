package com.jurisflow.modules.tenant;

import com.jurisflow.modules.tenant.dto.InviteMemberRequest;
import com.jurisflow.modules.tenant.dto.TenantMemberResponse;
import com.jurisflow.modules.tenant.dto.TenantRequest;
import com.jurisflow.modules.tenant.dto.TenantResponse;
import com.jurisflow.modules.tenant.dto.UpdateRoleRequest;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody TenantRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.create(request, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TenantRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tenantService.update(id, request, principal));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<Page<TenantMemberResponse>> getMembers(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tenantService.getMembers(id, pageable));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<TenantMemberResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.addMemberByEmail(id, request, principal));
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<Void> updateRole(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        tenantService.updateMemberRole(id, userId, request.role(), principal);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        tenantService.removeMember(id, userId, principal);
        return ResponseEntity.noContent().build();
    }
}
