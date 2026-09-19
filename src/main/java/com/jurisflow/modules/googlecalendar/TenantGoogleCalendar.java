package com.jurisflow.modules.googlecalendar;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_google_calendar")
public class TenantGoogleCalendar extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "connected_email", nullable = false)
    private String connectedEmail;

    @Builder.Default
    @Column(name = "calendar_id", nullable = false, length = 500)
    private String calendarId = "primary";

    @Column(name = "access_token", nullable = false, length = 2000)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 1000)
    private String refreshToken;

    @Column(name = "token_expiry", nullable = false)
    private OffsetDateTime tokenExpiry;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "connected_by", nullable = false)
    private UUID connectedBy;
}
