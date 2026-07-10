package com.jurisflow.modules.financeiro;

import com.jurisflow.modules.financeiro.dto.ClienteProcessoFinanceiroResponse;
import com.jurisflow.modules.financeiro.dto.ContratoRequest;
import com.jurisflow.modules.financeiro.dto.ContratoResponse;
import com.jurisflow.modules.financeiro.dto.ParcelaRequest;
import com.jurisflow.modules.financeiro.dto.ParcelaResponse;
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
public class FinanceiroController {

    private final ContratoService contratoService;

    @GetMapping("/processos/{processoId}/contrato")
    public ResponseEntity<ContratoResponse> getContrato(
            @PathVariable UUID processoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contratoService.getByProcesso(processoId, principal.getId()));
    }

    @PutMapping("/processos/{processoId}/contrato")
    public ResponseEntity<ContratoResponse> upsertContrato(
            @PathVariable UUID processoId,
            @Valid @RequestBody ContratoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contratoService.createOrUpdate(processoId, request, principal));
    }

    @PostMapping("/contratos/{contratoId}/parcelas")
    public ResponseEntity<ParcelaResponse> addParcela(
            @PathVariable UUID contratoId,
            @Valid @RequestBody ParcelaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contratoService.addParcela(contratoId, request, principal));
    }

    @PatchMapping("/parcelas/{id}/pagar")
    public ResponseEntity<ParcelaResponse> marcarPaga(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contratoService.marcarPaga(id, principal));
    }

    @GetMapping("/clientes/{clienteId}/processos")
    public ResponseEntity<List<ClienteProcessoFinanceiroResponse>> processosDoCliente(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contratoService.listProcessosDoCliente(clienteId, principal.getId()));
    }
}
