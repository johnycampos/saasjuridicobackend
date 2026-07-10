package com.jurisflow.modules.processolink;

import com.jurisflow.modules.processolink.dto.ProcessoLinkRequest;
import com.jurisflow.modules.processolink.dto.ProcessoLinkResponse;
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
@RequestMapping("/api/processos/{processoId}/links")
@RequiredArgsConstructor
public class ProcessoLinkController {

    private final ProcessoLinkService processoLinkService;

    @GetMapping
    public ResponseEntity<List<ProcessoLinkResponse>> list(@PathVariable UUID processoId) {
        return ResponseEntity.ok(processoLinkService.listByProcesso(processoId));
    }

    @PostMapping
    public ResponseEntity<ProcessoLinkResponse> create(
            @PathVariable UUID processoId,
            @Valid @RequestBody ProcessoLinkRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(processoLinkService.create(processoId, request, principal));
    }
}
