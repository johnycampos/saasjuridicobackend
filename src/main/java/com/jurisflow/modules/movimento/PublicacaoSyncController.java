package com.jurisflow.modules.movimento;

import com.jurisflow.modules.movimento.dto.PublicacaoSyncRequest;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de integracao maquina-a-maquina (mesmo script externo do
 * MovimentoSyncController) — recebe publicacoes (Comunica/DJEN) do mes de
 * cada processo. Grava na mesma tabela processo_movimentos, com
 * origem=PUBLICACAO e dedupe por hash_origem (externalId), ao contrario dos
 * movimentos (origem=MOVIMENTO, dedupe por codigo+data_hora).
 *
 * Autenticado via com.jurisflow.security.ApiKeyAuthFilter (header
 * X-Api-Key) — fica sob /api/integrations/**, ja liberado no SecurityConfig.
 */
@RestController
@RequestMapping("/api/integrations/publicacoes")
@RequiredArgsConstructor
public class PublicacaoSyncController {

    private final MovimentoSyncService movimentoSyncService;

    @PostMapping("/sync")
    public ResponseEntity<PublicacaoSyncResponse> sync(@Valid @RequestBody PublicacaoSyncRequest request) {
        return ResponseEntity.ok(new PublicacaoSyncResponse(movimentoSyncService.syncPublicacoes(request.publicacoes())));
    }
}
