package com.jurisflow.modules.tenant.dto;

import com.jurisflow.modules.tenant.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
    @NotBlank @Email String email,
    TenantRole role
) {}
