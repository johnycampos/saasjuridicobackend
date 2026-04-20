package com.jurisflow.modules.tenant.dto;

import com.jurisflow.modules.tenant.TenantMemberStatus;
import com.jurisflow.modules.tenant.TenantRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantMemberResponse(
    UUID id,
    UUID userId,
    String userEmail,
    String userNome,
    String userAvatarUrl,
    TenantRole role,
    TenantMemberStatus status,
    LocalDateTime joinedAt
) {}
