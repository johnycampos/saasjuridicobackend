package com.jurisflow.modules.tarefa;

import com.jurisflow.modules.tarefa.dto.TarefaRequest;
import com.jurisflow.modules.tarefa.dto.TarefaResponse;
import com.jurisflow.modules.tarefa.dto.ToggleConcluidaRequest;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class TarefaController {

    private final TarefaService tarefaService;

    @GetMapping("/processos/{processoId}/tarefas")
    public ResponseEntity<List<TarefaResponse>> listByProcesso(@PathVariable UUID processoId) {
        return ResponseEntity.ok(tarefaService.listByProcesso(processoId));
    }

    @PostMapping("/processos/{processoId}/tarefas")
    public ResponseEntity<TarefaResponse> create(
            @PathVariable UUID processoId,
            @Valid @RequestBody TarefaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tarefaService.create(processoId, request, principal));
    }

    @PatchMapping("/tarefas/{id}/concluida")
    public ResponseEntity<TarefaResponse> toggleConcluida(
            @PathVariable UUID id,
            @Valid @RequestBody ToggleConcluidaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tarefaService.toggleConcluida(id, request.concluida(), principal));
    }
}
