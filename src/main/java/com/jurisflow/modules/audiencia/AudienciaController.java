package com.jurisflow.modules.audiencia;

import com.jurisflow.modules.audiencia.dto.AudienciaRequest;
import com.jurisflow.modules.audiencia.dto.AudienciaResponse;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audiencias")
@RequiredArgsConstructor
public class AudienciaController {

    private final AudienciaService audienciaService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping
    public ResponseEntity<List<AudienciaResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(audienciaService.list(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AudienciaResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(audienciaService.findById(id, tenantId));
    }

    @GetMapping("/processo/{processoId}")
    public ResponseEntity<List<AudienciaResponse>> listByProcesso(
            @PathVariable UUID processoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(audienciaService.listByProcesso(processoId, tenantId));
    }

    @PostMapping
    public ResponseEntity<AudienciaResponse> create(
            @Valid @RequestBody AudienciaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(audienciaService.create(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AudienciaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AudienciaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(audienciaService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        audienciaService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
