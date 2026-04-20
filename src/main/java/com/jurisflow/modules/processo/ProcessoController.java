package com.jurisflow.modules.processo;

import com.jurisflow.modules.processo.dto.MoveProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
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
@RequestMapping("/api/processos")
@RequiredArgsConstructor
public class ProcessoController {

    private final ProcessoService processoService;

    @GetMapping
    public ResponseEntity<Page<ProcessoResponse>> list(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(processoService.search(q, pageable));
        }
        if (groupId != null) {
            return ResponseEntity.ok(processoService.listByGroup(groupId, pageable));
        }
        return ResponseEntity.ok(processoService.listByTenant(pageable));
    }

    @PostMapping
    public ResponseEntity<ProcessoResponse> create(
            @Valid @RequestBody ProcessoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(processoService.create(request, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessoResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(processoService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcessoResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessoRequest request) {
        return ResponseEntity.ok(processoService.update(id, request));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<ProcessoResponse> move(
            @PathVariable UUID id,
            @Valid @RequestBody MoveProcessoRequest request) {
        return ResponseEntity.ok(processoService.move(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        processoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
