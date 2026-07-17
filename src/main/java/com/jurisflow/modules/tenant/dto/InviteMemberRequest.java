package com.jurisflow.modules.tenant.dto;

import com.jurisflow.modules.tenant.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record InviteMemberRequest(
    @NotBlank @Email String email,
    TenantRole role,
    List<UUID> groupIds
) {}
