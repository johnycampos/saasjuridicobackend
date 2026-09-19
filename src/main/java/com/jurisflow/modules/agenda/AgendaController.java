package com.jurisflow.modules.agenda;

import com.jurisflow.modules.agenda.dto.AgendaCompletaResponse;
import com.jurisflow.modules.agenda.dto.AgendaEventoResponse;
import com.jurisflow.modules.agenda.dto.AgendaResumoResponse;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping("/completa")
    public ResponseEntity<AgendaCompletaResponse> getAgendaCompleta(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(agendaService.getAgendaCompleta(ano, mes, principal));
    }

    @GetMapping("/mes")
    public ResponseEntity<List<AgendaEventoResponse>> getEventosMes(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(agendaService.getEventosMes(ano, mes, principal));
    }

    @GetMapping("/resumo")
    public ResponseEntity<AgendaResumoResponse> getResumoMes(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(agendaService.getResumoMes(ano, mes, principal));
    }
}
