package com.jurisflow.modules.movimento;

import com.jurisflow.modules.movimento.dto.MovimentoSyncRequest;
import com.jurisflow.modules.movimento.dto.MovimentoSyncResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de integracao maquina-a-maquina (script externo que consulta
 * DataJud/tribunal e empurra os movimentos pra ca). Autenticado via
 * com.jurisflow.security.ApiKeyAuthFilter (header X-Api-Key), NAO via
 * OAuth2/JWT de usuario — por isso fica sob /api/integrations/**, path
 * liberado de proposito no SecurityConfig e coberto pelo filtro de API key.
 */
@RestController
@RequestMapping("/api/integrations/movimentos")
@RequiredArgsConstructor
public class MovimentoSyncController {

    private final MovimentoSyncService movimentoSyncService;

    @PostMapping("/sync")
    public ResponseEntity<MovimentoSyncResponse> sync(@Valid @RequestBody MovimentoSyncRequest request) {
        return ResponseEntity.ok(new MovimentoSyncResponse(movimentoSyncService.sync(request.processos())));
    }
}
