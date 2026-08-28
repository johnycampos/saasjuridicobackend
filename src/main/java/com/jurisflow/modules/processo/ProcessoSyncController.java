package com.jurisflow.modules.processo;

import com.jurisflow.modules.processo.dto.ProcessoNumeroResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint de integracao maquina-a-maquina (mesmo script externo do
 * MovimentoSyncController) — lista processos ativos com numero preenchido
 * pra alimentar o loop do script antes dele consultar o DataJud e mandar
 * os movimentos de volta pra /api/integrations/movimentos/sync.
 *
 * Autenticado via com.jurisflow.security.ApiKeyAuthFilter (header
 * X-Api-Key), nao OAuth2/JWT de usuario — fica sob /api/integrations/**,
 * ja liberado e coberto pelo filtro no SecurityConfig.
 */
@RestController
@RequestMapping("/api/integrations/processos")
@RequiredArgsConstructor
public class ProcessoSyncController {

    private final ProcessoService processoService;

    @GetMapping
    public ResponseEntity<Page<ProcessoNumeroResponse>> list(
            @RequestParam(required = false) UUID tenantId,
            @PageableDefault(size = 200, sort = "numeroProcesso") Pageable pageable) {
        return ResponseEntity.ok(processoService.listParaSincronizacaoMovimentos(tenantId, pageable));
    }
}
