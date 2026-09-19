package com.jurisflow.modules.googlecalendar.dto;

import java.time.LocalDateTime;

public record GoogleCalendarStatusResponse(
        boolean connected,
        String email,
        String status,
        LocalDateTime connectedAt,
        String calendarId
) {}
