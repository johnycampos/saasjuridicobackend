package com.jurisflow.modules.tenant.dto;

import com.jurisflow.modules.tenant.TenantRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull TenantRole role) {}
