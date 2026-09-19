package com.jurisflow.modules.googlecalendar.dto;

public record GoogleCalendarListItem(
    String id,
    String summary,
    String backgroundColor,
    String accessRole,
    boolean primary
) {}
