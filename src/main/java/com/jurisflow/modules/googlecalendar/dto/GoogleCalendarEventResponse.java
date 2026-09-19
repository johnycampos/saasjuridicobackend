package com.jurisflow.modules.googlecalendar.dto;

import java.time.LocalDateTime;

public record GoogleCalendarEventResponse(
        String id,
        String summary,
        String description,
        LocalDateTime start,
        LocalDateTime end,
        String htmlLink
) {}
