package com.jurisflow.modules.googlecalendar;

import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarEventResponse;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarListItem;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarStatusResponse;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/google-calendar")
@RequiredArgsConstructor
public class GoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;
    private final TenantAccessGuard tenantAccessGuard;

    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;

    @GetMapping("/status")
    public ResponseEntity<GoogleCalendarStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(googleCalendarService.getStatus(tenantId));
    }

    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(@AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        String url = googleCalendarService.getAuthorizationUrl(tenantId, principal.getId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/callback")
    public void handleCallback(@RequestParam("code") String code,
                               @RequestParam("state") String state,
                               HttpServletResponse response) throws IOException {
        googleCalendarService.handleCallback(code, state);
        response.sendRedirect(frontendUrl + "/dashboard?calendar=connected");
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        googleCalendarService.disconnect(tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendars")
    public ResponseEntity<List<GoogleCalendarListItem>> listCalendars(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(googleCalendarService.listCalendars(tenantId));
    }

    @PutMapping("/calendar")
    public ResponseEntity<Void> updateCalendarId(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireMinRole(tenantId, principal.getId(), TenantRole.ADMIN);
        String calendarId = body.get("calendarId");
        googleCalendarService.updateCalendarId(tenantId, calendarId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/events")
    public ResponseEntity<List<GoogleCalendarEventResponse>> getEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeMin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeMax
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tenantAccessGuard.requireActiveMember(tenantId, principal.getId());
        return ResponseEntity.ok(googleCalendarService.getEvents(tenantId, timeMin, timeMax));
    }
}
