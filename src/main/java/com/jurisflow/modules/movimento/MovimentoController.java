package com.jurisflow.modules.movimento;

import com.jurisflow.modules.movimento.dto.MovimentoResponse;
import com.jurisflow.modules.movimento.dto.ToggleVisualizadoRequest;
import com.jurisflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MovimentoController {

    private final MovimentoService movimentoService;

    @GetMapping("/processos/{processoId}/movimentos")
    public ResponseEntity<List<MovimentoResponse>> listByProcesso(@PathVariable UUID processoId) {
        return ResponseEntity.ok(movimentoService.listByProcesso(processoId));
    }

    @PatchMapping("/movimentos/{id}/visualizado")
    public ResponseEntity<MovimentoResponse> toggleVisualizado(
            @PathVariable UUID id,
            @Valid @RequestBody ToggleVisualizadoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(movimentoService.toggleVisualizado(id, request.visualizado(), principal));
    }
}
