package com.jurisflow.modules.dashboard;

import com.jurisflow.modules.dashboard.dto.AgendaTarefaResponse;
import com.jurisflow.modules.dashboard.dto.DashboardResumoResponse;
import com.jurisflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoResponse> getResumo(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getResumo(principal));
    }

    @GetMapping("/agenda-semana")
    public ResponseEntity<List<AgendaTarefaResponse>> getAgendaSemana(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getAgendaSemana(principal));
    }
}
